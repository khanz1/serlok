import type { ServerWebSocket } from "bun";
import type { WsData } from "../ws/ws.types";
import {
  roomRepository,
  type RoomRepository,
  ALLOWED_LOCATION_INTERVALS_MS,
  ALLOWED_SHAKE_THRESHOLDS,
} from "./room.repository";

export type RoomCreateOptions = {
  shakeThreshold?: number;
  locationIntervalMs?: number;
};

export type RoomServiceOptions = {
  emptySocketCloseMs?: number;
};

export const EMPTY_SOCKET_CLOSE_MS = 4 * 60 * 60 * 1000;

type DetachedRoom = {
  code: string;
  masterId: string;
  isSharing: boolean;
  shakeThreshold: number;
  locationIntervalMs: number;
  members: Map<string, ServerWebSocket<WsData>>;
};

export class RoomService {
  private roomSockets = new Map<string, Map<string, ServerWebSocket<WsData>>>();
  private lastLocation = new Map<
    string,
    { roomCode: string; latitude: number; longitude: number; accuracy?: number | null }
  >();
  private emptyCloseTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private readonly emptySocketCloseMs: number;

  constructor(
    private readonly roomRepository: RoomRepository,
    options: RoomServiceOptions = {}
  ) {
    this.emptySocketCloseMs = options.emptySocketCloseMs ?? EMPTY_SOCKET_CLOSE_MS;
  }

  dispose(): void {
    for (const timer of this.emptyCloseTimers.values()) {
      clearTimeout(timer);
    }
    this.emptyCloseTimers.clear();
  }

  private generateCode(): string {
    const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    return Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join(
      ""
    );
  }

  private async uniqueCode(): Promise<string> {
    let code = this.generateCode();
    while (await this.roomRepository.findByCode(code)) code = this.generateCode();
    return code;
  }

  isValidShakeThreshold(value: number): boolean {
    return (ALLOWED_SHAKE_THRESHOLDS as readonly number[]).includes(value);
  }

  isValidLocationIntervalMs(value: number): boolean {
    return (ALLOWED_LOCATION_INTERVALS_MS as readonly number[]).includes(value);
  }

  isClosed(room: { closedAt: Date | null }): boolean {
    return room.closedAt != null;
  }

  async createRoom(ws: ServerWebSocket<WsData>, options: RoomCreateOptions = {}) {
    const { userId } = ws.data;
    const code = await this.uniqueCode();
    const shakeThreshold = options.shakeThreshold ?? 3.5;
    const locationIntervalMs = options.locationIntervalMs ?? 10_000;
    const room = await this.roomRepository.create(code, userId, {
      shakeThreshold,
      locationIntervalMs,
    });
    await this.roomRepository.addMember(code, userId);
    this.roomSockets.set(code, new Map([[userId, ws]]));
    ws.data.roomCode = code;
    return room;
  }

  async joinRoom(
    code: string,
    ws: ServerWebSocket<WsData>
  ): Promise<"ok" | "not_found" | "closed"> {
    const room = await this.roomRepository.findByCode(code);
    if (!room) return "not_found";
    if (this.isClosed(room)) return "closed";
    await this.roomRepository.addMember(code, ws.data.userId);
    this.cancelEmptySocketClose(code);
    if (!this.roomSockets.has(code)) this.roomSockets.set(code, new Map());
    this.roomSockets.get(code)!.set(ws.data.userId, ws);
    ws.data.roomCode = code;
    return "ok";
  }

  async leaveRoom(ws: ServerWebSocket<WsData>): Promise<
    | {
      softClosed: true;
      code: string;
      remaining: ServerWebSocket<WsData>[];
    }
    | {
      softClosed?: false;
      code: string;
      masterId: string;
      isSharing: boolean;
      shakeThreshold: number;
      locationIntervalMs: number;
      members: Map<string, ServerWebSocket<WsData>>;
    }
    | null
  > {
    const code = ws.data.roomCode;
    if (!code) return null;
    const { userId } = ws.data;

    const room = await this.roomRepository.findByCode(code);
    const wasHost = Boolean(room && userId === room.masterId);

    await this.roomRepository.removeMember(code, userId);
    this.lastLocation.delete(userId);
    const members = this.roomSockets.get(code);
    members?.delete(userId);
    ws.data.roomCode = undefined;

    const dbMembers = await this.roomRepository.getMembers(code);
    const shouldClose = wasHost || dbMembers.length === 0;

    if (shouldClose) {
      const remaining = members && members.size > 0 ? Array.from(members.values()) : [];
      for (const remainingWs of remaining) {
        remainingWs.data.roomCode = undefined;
      }
      this.cancelEmptySocketClose(code);
      this.roomSockets.delete(code);
      this.clearRoomLocations(code);
      await this.roomRepository.clearMembers(code);
      await this.roomRepository.softCloseRoom(code);
      return { softClosed: true, code, remaining };
    }

    if (!room || this.isClosed(room)) return null;

    if (!members || members.size === 0) {
      this.roomSockets.delete(code);
      this.scheduleEmptySocketClose(code);
    }

    return {
      softClosed: false,
      code,
      masterId: room.masterId,
      isSharing: room.isSharing,
      shakeThreshold: room.shakeThreshold,
      locationIntervalMs: room.locationIntervalMs,
      members: members ?? new Map(),
    };
  }

  async detachSocket(ws: ServerWebSocket<WsData>): Promise<DetachedRoom | null> {
    const code = ws.data.roomCode;
    if (!code) return null;
    const { userId } = ws.data;
    const room = await this.roomRepository.findByCode(code);
    const members = this.roomSockets.get(code);
    members?.delete(userId);
    ws.data.roomCode = undefined;

    if (!members || members.size === 0) {
      this.roomSockets.delete(code);
      this.scheduleEmptySocketClose(code);
    }

    if (!room || this.isClosed(room)) return null;

    return {
      code,
      masterId: room.masterId,
      isSharing: room.isSharing,
      shakeThreshold: room.shakeThreshold,
      locationIntervalMs: room.locationIntervalMs,
      members: members ?? new Map(),
    };
  }

  async rebindMembership(ws: ServerWebSocket<WsData>) {
    const room = await this.roomRepository.findOpenRoomForUser(ws.data.userId);
    if (!room || this.isClosed(room)) return null;
    this.cancelEmptySocketClose(room.code);
    if (!this.roomSockets.has(room.code)) this.roomSockets.set(room.code, new Map());
    this.roomSockets.get(room.code)!.set(ws.data.userId, ws);
    ws.data.roomCode = room.code;
    return room;
  }

  async softCloseRoom(code: string): Promise<void> {
    this.cancelEmptySocketClose(code);
    this.roomSockets.delete(code);
    this.clearRoomLocations(code);
    await this.roomRepository.clearMembers(code);
    await this.roomRepository.softCloseRoom(code);
  }

  private scheduleEmptySocketClose(code: string): void {
    this.cancelEmptySocketClose(code);
    const timer = setTimeout(() => {
      this.emptyCloseTimers.delete(code);
      void this.softCloseRoom(code);
    }, this.emptySocketCloseMs);
    timer.unref?.();
    this.emptyCloseTimers.set(code, timer);
  }

  private cancelEmptySocketClose(code: string): void {
    const timer = this.emptyCloseTimers.get(code);
    if (!timer) return;
    clearTimeout(timer);
    this.emptyCloseTimers.delete(code);
  }

  async setSharing(code: string, isSharing: boolean) {
    if (isSharing) this.clearRoomLocations(code);
    await this.roomRepository.updateSharing(code, isSharing);
  }

  async setSettings(code: string, shakeThreshold: number, locationIntervalMs: number) {
    await this.roomRepository.updateSettings(code, { shakeThreshold, locationIntervalMs });
  }

  async setShakeThreshold(code: string, shakeThreshold: number) {
    await this.roomRepository.updateShakeThreshold(code, shakeThreshold);
  }

  async getRoom(code: string) {
    return this.roomRepository.findByCode(code);
  }

  async getMemberIds(code: string): Promise<string[]> {
    return this.roomRepository.getMembers(code);
  }

  updateLastLocation(
    roomCode: string,
    userId: string,
    latitude: number,
    longitude: number,
    accuracy?: number
  ) {
    this.lastLocation.set(
      userId,
      Number.isFinite(accuracy) && (accuracy ?? -1) >= 0
        ? { roomCode, latitude, longitude, accuracy }
        : { roomCode, latitude, longitude }
    );
  }

  getLastLocation(roomCode: string, userId: string) {
    const location = this.lastLocation.get(userId);
    if (!location || location.roomCode !== roomCode) return undefined;
    const { roomCode: _roomCode, ...coordinates } = location;
    return coordinates;
  }

  private clearRoomLocations(roomCode: string) {
    for (const [userId, location] of this.lastLocation) {
      if (location.roomCode === roomCode) this.lastLocation.delete(userId);
    }
  }

  getSockets(code: string) {
    return this.roomSockets.get(code);
  }

  broadcastToRoom(code: string, payload: string, excludeUserId?: string) {
    const members = this.roomSockets.get(code);
    if (!members) return;
    for (const [id, ws] of members) {
      if (id !== excludeUserId) ws.send(payload);
    }
  }

  roomStatePayload(
    code: string,
    masterUserId: string,
    isSharing: boolean,
    shakeThreshold = 3.5,
    locationIntervalMs = 10_000
  ) {
    const sockets = this.roomSockets.get(code);
    return {
      code,
      masterId: masterUserId,
      isSharing,
      shakeThreshold,
      locationIntervalMs,
      members: sockets
        ? Array.from(sockets.entries()).map(([userId, ws]) => ({
          userId,
          deviceInstanceId: ws.data.deviceInstanceId,
          name: ws.data.name,
          email: ws.data.email,
          manufacturer: ws.data.manufacturer,
          model: ws.data.model,
          androidVersion: ws.data.androidVersion,
        }))
        : [],
    };
  }
}

export const roomService = new RoomService(roomRepository);
