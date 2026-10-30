import type { ServerWebSocket } from "bun";
import type { WsData } from "./ws.types";
import { short, sendError } from "./ws.manager";
import { roomController as defaultRoomController } from "../room/room.module";
import { roomService as defaultRoomService } from "../room/room.service";
import type { RoomController } from "../room/room.controller";
import type { RoomService } from "../room/room.service";
import { LocationThrottle } from "./throttle";
import { ts } from "../../utils/log";

export interface RoomWsDeps {
  roomController?: Pick<
    RoomController,
    | "createViaWs"
    | "joinViaWs"
    | "leaveViaWs"
    | "startViaWs"
    | "stopViaWs"
    | "disconnectViaWs"
    | "rebindViaWs"
  >;
  roomService?: Pick<
    RoomService,
    | "broadcastToRoom"
    | "updateLastLocation"
    | "getRoom"
    | "setSettings"
    | "setShakeThreshold"
    | "isValidShakeThreshold"
    | "isValidLocationIntervalMs"
    | "isClosed"
  >;
  throttle?: LocationThrottle;
  now?: () => number;
}

export interface RoomWsHandler {
  handleRoomMessage(
    ws: ServerWebSocket<WsData>,
    type: string,
    payload: Record<string, unknown>
  ): Promise<boolean>;
  handleRoomDisconnect(ws: ServerWebSocket<WsData>): Promise<{ code: string } | undefined>;
  handleRoomReconnect(ws: ServerWebSocket<WsData>): Promise<void>;
  cleanupRoomDevice(userId: string): void;
}

function roomStateCleared(now: number): string {
  return JSON.stringify({ type: "room_state", payload: null, timestamp: now });
}

export function createRoomWsHandler(deps: RoomWsDeps = {}): RoomWsHandler {
  const roomController = deps.roomController ?? defaultRoomController;
  const roomService = deps.roomService ?? defaultRoomService;
  const throttle = deps.throttle ?? new LocationThrottle();
  const now = deps.now ?? Date.now;

  function cleanupRoomDevice(userId: string): void {
    throttle.delete(userId);
  }

  async function handleRoomDisconnect(ws: ServerWebSocket<WsData>) {
    const { code, messages } = await roomController.disconnectViaWs(ws);

    if (code && messages) {
      for (const msg of messages) {
        roomService.broadcastToRoom(code, JSON.stringify(msg));
      }
    }

    return code ? { code } : undefined;
  }

  async function handleRoomReconnect(ws: ServerWebSocket<WsData>): Promise<void> {
    const rebound = await roomController.rebindViaWs(ws);
    if (!rebound) return;
    ws.send(JSON.stringify(rebound.selfMessage));
    roomService.broadcastToRoom(
      rebound.code,
      JSON.stringify(rebound.broadcastMessage),
      ws.data.userId
    );
  }

  async function handleRoomMessage(
    ws: ServerWebSocket<WsData>,
    type: string,
    payload: Record<string, unknown>
  ): Promise<boolean> {
    const { userId } = ws.data;

    switch (type) {
      case "location_update": {
        const p = payload as Record<string, number>;
        const latitude = p.latitude;
        const longitude = p.longitude;

        if (
          typeof latitude !== "number" ||
          typeof longitude !== "number" ||
          !Number.isFinite(latitude) ||
          !Number.isFinite(longitude) ||
          latitude < -90 ||
          latitude > 90 ||
          longitude < -180 ||
          longitude > 180
        ) {
          return true;
        }

        if (ws.data.roomCode) {
          const roomCode = ws.data.roomCode;
          const room = await roomService.getRoom(roomCode);
          if (room?.isSharing && !roomService.isClosed(room)) {
            roomService.broadcastToRoom(
              roomCode,
              JSON.stringify({
                type: "location_update",
                payload: {
                  userId,
                  latitude,
                  longitude,
                  accuracy: p.accuracy ?? 0,
                  speed: p.speed ?? 0,
                },
                timestamp: now(),
              }),
              userId
            );
            roomService.updateLastLocation(roomCode, userId, latitude, longitude, p.accuracy);
          }
        }

        const snapshot = throttle.record(userId, now());
        if (snapshot) {
          console.log(
            `${ts()} LOCATION ${short(userId).padEnd(12)} ` +
            `${snapshot.count} fixes (${snapshot.fps.toFixed(2)}/s)  ` +
            `lat=${p.latitude?.toFixed(5)} lng=${p.longitude?.toFixed(5)}`
          );
        }
        return true;
      }

      case "room_create": {
        const shakeThreshold = Number(payload.shakeThreshold ?? 3.5);
        const locationIntervalMs = Number(payload.locationIntervalMs ?? 10_000);
        if (
          !roomService.isValidShakeThreshold(shakeThreshold) ||
          !roomService.isValidLocationIntervalMs(locationIntervalMs)
        ) {
          sendError(ws, "Invalid room settings");
          return true;
        }
        const message = await roomController.createViaWs(ws, {
          shakeThreshold,
          locationIntervalMs,
        });
        ws.send(JSON.stringify(message));
        return true;
      }

      case "room_join": {
        const code = (payload.code as string | undefined)?.toUpperCase();
        if (!code) {
          sendError(ws, "Missing room code");
          return true;
        }
        const result = await roomController.joinViaWs(ws, code);
        if (!result.success || !result.messages) {
          if (result.error === "closed") {
            sendError(ws, `Room '${code}' is closed`);
          } else {
            sendError(ws, `Room '${code}' not found`);
          }
          return true;
        }

        for (const msg of result.messages) {
          if (msg.type === "user_joined") {
            ws.send(
              JSON.stringify({
                type: "room_state",
                payload: (result.messages[0] as { payload: unknown }).payload,
                timestamp: now(),
              })
            );
            roomService.broadcastToRoom(code, JSON.stringify(msg), userId);
          } else {
            roomService.broadcastToRoom(code, JSON.stringify(msg));
          }
        }
        return true;
      }

      case "room_leave": {
        const code = ws.data.roomCode;
        const { leaveMessage, remaining, softClosed } = await roomController.leaveViaWs(ws);
        if (leaveMessage) {
          ws.send(JSON.stringify(leaveMessage));
        }
        if (softClosed) {
          const cleared = roomStateCleared(now());
          for (const remainingWs of remaining) {
            remainingWs.send(cleared);
          }
          return true;
        }
        if (code && leaveMessage?.payload) {
          roomService.broadcastToRoom(code, JSON.stringify(leaveMessage));
        }
        return true;
      }

      case "room_start": {
        const message = await roomController.startViaWs(ws);
        if (!message) {
          sendError(ws, "Not in a room or not the host");
          return true;
        }
        roomService.broadcastToRoom(ws.data.roomCode!, JSON.stringify(message));
        return true;
      }

      case "room_stop": {
        const message = await roomController.stopViaWs(ws);
        if (!message) {
          sendError(ws, "Not in a room or not the host");
          return true;
        }
        roomService.broadcastToRoom(ws.data.roomCode!, JSON.stringify(message));
        return true;
      }

      case "room_update_settings": {
        const code = ws.data.roomCode;
        const shakeThreshold = Number(payload.shakeThreshold);
        const locationIntervalMs = Number(payload.locationIntervalMs);
        if (
          !code ||
          !roomService.isValidShakeThreshold(shakeThreshold) ||
          !roomService.isValidLocationIntervalMs(locationIntervalMs)
        ) {
          sendError(ws, "Invalid room settings");
          return true;
        }
        const room = await roomService.getRoom(code);
        if (!room || roomService.isClosed(room) || room.masterId !== userId) {
          sendError(ws, "Not in a room or not the host");
          return true;
        }
        if (room.isSharing) {
          sendError(ws, "Stop the safety session before updating room settings");
          return true;
        }
        await roomService.setSettings(code, shakeThreshold, locationIntervalMs);
        roomService.broadcastToRoom(
          code,
          JSON.stringify({
            type: "room_settings",
            payload: {
              shakeThreshold,
              locationIntervalMs,
            },
            timestamp: now(),
          })
        );
        return true;
      }

      default:
        return false;
    }
  }

  return { handleRoomMessage, handleRoomDisconnect, handleRoomReconnect, cleanupRoomDevice };
}

const defaultHandler = createRoomWsHandler();
export const handleRoomMessage = defaultHandler.handleRoomMessage;
export const handleRoomDisconnect = defaultHandler.handleRoomDisconnect;
export const handleRoomReconnect = defaultHandler.handleRoomReconnect;
export const cleanupRoomDevice = defaultHandler.cleanupRoomDevice;
