import type { ServerWebSocket } from "bun";
import type { RoomService, RoomCreateOptions } from "./room.service";
import type { WsData } from "../ws/ws.types";
import { short } from "../ws/ws.manager";
import { ts } from "../../utils/log";
import {
  sosRepository as defaultSosRepository,
  type PublicIncident,
  type SosRepository,
} from "../sos/sos.repository";

type RoomIncidentReader = Pick<SosRepository, "listPublicByRoom">;

export class RoomController {
  constructor(
    private readonly roomService: RoomService,
    private readonly sosRepository: RoomIncidentReader = defaultSosRepository
  ) {
    this.createViaWs = this.createViaWs.bind(this);
  }

  private async roomStateWithIncidents(
    code: string,
    masterUserId: string,
    isSharing: boolean,
    shakeThreshold = 3.5,
    locationIntervalMs = 10_000
  ): Promise<{
    code: string;
    masterId: string;
    isSharing: boolean;
    shakeThreshold: number;
    locationIntervalMs: number;
    members: unknown[];
    incidents: PublicIncident[];
  }> {
    const state = this.roomService.roomStatePayload(
      code,
      masterUserId,
      isSharing,
      shakeThreshold,
      locationIntervalMs
    );
    const incidents = await this.sosRepository.listPublicByRoom(code);
    return { ...state, incidents };
  }

  async createViaWs(
    ws: ServerWebSocket<WsData>,
    options: RoomCreateOptions = {}
  ): Promise<{ type: string; payload: unknown; timestamp: number }> {
    if (ws.data.roomCode) await this.roomService.leaveRoom(ws);
    const room = await this.roomService.createRoom(ws, options);

    console.log(`${ts()} ROOM_NEW ${short(ws.data.userId).padEnd(12)} code=${room.code}`);

    return {
      type: "room_state",
      payload: await this.roomStateWithIncidents(
        room.code,
        room.masterId,
        room.isSharing,
        room.shakeThreshold,
        room.locationIntervalMs
      ),
      timestamp: Date.now(),
    };
  }

  async joinViaWs(
    ws: ServerWebSocket<WsData>,
    code: string
  ): Promise<{
    success: boolean;
    error?: "not_found" | "closed";
    messages?: Array<{ type: string; payload: unknown; timestamp: number }>;
  }> {
    const { userId, name, email } = ws.data;

    if (ws.data.roomCode && ws.data.roomCode !== code) {
      await this.roomService.leaveRoom(ws);
    }
    const joined = await this.roomService.joinRoom(code, ws);
    if (joined !== "ok") {
      return { success: false, error: joined };
    }

    const room = await this.roomService.getRoom(code);
    const statePayload = await this.roomStateWithIncidents(
      code,
      room!.masterId,
      room!.isSharing,
      room!.shakeThreshold,
      room!.locationIntervalMs
    );

    console.log(
      `${ts()} ROOM_IN  ${short(userId).padEnd(12)} ` +
      `code=${code}  members=${this.roomService.getSockets(code)?.size ?? 0}`
    );

    return {
      success: true,
      messages: [
        { type: "room_state", payload: statePayload, timestamp: Date.now() },
        {
          type: "user_joined",
          payload: { userId, name, email },
          timestamp: Date.now(),
        },
      ],
    };
  }

  async leaveViaWs(ws: ServerWebSocket<WsData>): Promise<{
    leaveMessage: { type: string; payload: unknown; timestamp: number } | null;
    remaining: ServerWebSocket<WsData>[];
    softClosed: boolean;
    code: string | null;
  }> {
    const { userId } = ws.data;
    const code = ws.data.roomCode;
    if (!code) {
      return { leaveMessage: null, remaining: [], softClosed: false, code: null };
    }

    const result = await this.roomService.leaveRoom(ws);

    if (result?.softClosed) {
      console.log(`${ts()} ROOM_OUT ${short(userId).padEnd(12)} code=${code} soft_closed`);
      return {
        leaveMessage: { type: "room_state", payload: null, timestamp: Date.now() },
        remaining: result.remaining ?? [],
        softClosed: true,
        code,
      };
    }

    if (result) {
      console.log(
        `${ts()} ROOM_OUT ${short(userId).padEnd(12)} ` +
        `code=${code}  remaining=${result.members?.size ?? 0}`
      );
      return {
        leaveMessage: {
          type: "room_state",
          payload: await this.roomStateWithIncidents(
            result.code,
            result.masterId,
            result.isSharing,
            result.shakeThreshold,
            result.locationIntervalMs
          ),
          timestamp: Date.now(),
        },
        remaining: [],
        softClosed: false,
        code,
      };
    }

    console.log(`${ts()} ROOM_OUT ${short(userId).padEnd(12)} code=${code}`);
    return {
      leaveMessage: { type: "room_state", payload: null, timestamp: Date.now() },
      remaining: [],
      softClosed: false,
      code,
    };
  }

  async startViaWs(
    ws: ServerWebSocket<WsData>
  ): Promise<{ type: string; payload: unknown; timestamp: number } | null> {
    const { userId } = ws.data;
    const code = ws.data.roomCode;
    if (!code) return null;

    const room = await this.roomService.getRoom(code);
    if (!room || this.roomService.isClosed(room)) return null;
    if (room.masterId !== userId) return null;

    await this.roomService.setSharing(code, true);

    console.log(`${ts()} SHARING+ ${short(userId).padEnd(12)} code=${code}`);

    return {
      type: "room_start",
      payload: await this.roomStateWithIncidents(
        code,
        room.masterId,
        true,
        room.shakeThreshold,
        room.locationIntervalMs
      ),
      timestamp: Date.now(),
    };
  }

  async stopViaWs(
    ws: ServerWebSocket<WsData>
  ): Promise<{ type: string; payload: unknown; timestamp: number } | null> {
    const { userId } = ws.data;
    const code = ws.data.roomCode;
    if (!code) return null;

    const room = await this.roomService.getRoom(code);
    if (!room || this.roomService.isClosed(room)) return null;
    if (room.masterId !== userId) return null;

    await this.roomService.setSharing(code, false);

    console.log(`${ts()} SHARING- ${short(userId).padEnd(12)} code=${code}`);

    return {
      type: "room_stop",
      payload: await this.roomStateWithIncidents(
        code,
        room.masterId,
        false,
        room.shakeThreshold,
        room.locationIntervalMs
      ),
      timestamp: Date.now(),
    };
  }

  async disconnectViaWs(ws: ServerWebSocket<WsData>): Promise<{
    code?: string;
    messages?: Array<{ type: string; payload: unknown; timestamp: number }>;
  }> {
    const { userId, name } = ws.data;
    const result = await this.roomService.detachSocket(ws);

    if (!result) {
      return {};
    }

    const messages = [
      {
        type: "room_state",
        payload: await this.roomStateWithIncidents(
          result.code,
          result.masterId,
          result.isSharing,
          result.shakeThreshold,
          result.locationIntervalMs
        ),
        timestamp: Date.now(),
      },
      {
        type: "user_offline",
        payload: { userId, name },
        timestamp: Date.now(),
      },
    ];

    return { code: result.code, messages };
  }

  async rebindViaWs(ws: ServerWebSocket<WsData>): Promise<{
    code: string;
    selfMessage: { type: string; payload: unknown; timestamp: number };
    broadcastMessage: { type: string; payload: unknown; timestamp: number };
  } | null> {
    const room = await this.roomService.rebindMembership(ws);
    if (!room) return null;

    const { userId, name, email } = ws.data;
    const timestamp = Date.now();
    return {
      code: room.code,
      selfMessage: {
        type: "room_state",
        payload: await this.roomStateWithIncidents(
          room.code,
          room.masterId,
          room.isSharing,
          room.shakeThreshold,
          room.locationIntervalMs
        ),
        timestamp,
      },
      broadcastMessage: {
        type: "user_joined",
        payload: { userId, name, email },
        timestamp,
      },
    };
  }
}
