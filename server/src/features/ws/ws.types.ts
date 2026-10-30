import { z } from "zod";

export const ClientMessageSchema = z.object({
  type: z.enum([
    "sos_trigger",
    "ping",
    "room_create",
    "room_join",
    "room_leave",
    "room_start",
    "room_stop",
    "room_update_settings",
    "location_update",
  ]),
  payload: z.record(z.string(), z.unknown()).optional(),
  timestamp: z.number(),
  idempotencyKey: z.uuid().optional(),
});

export type ClientMessage = z.infer<typeof ClientMessageSchema>;

export type ServerMessageType =
  | "pong"
  | "room_state"
  | "room_error"
  | "room_alert"
  | "room_settings"
  | "location_update"
  | "user_joined"
  | "user_offline"
  | "room_start"
  | "room_stop";

export interface ServerMessage<TPayload = unknown> {
  type: ServerMessageType;
  payload?: TPayload;
  timestamp: number;
}

export interface WsData {
  userId: string;
  deviceInstanceId: string;
  name: string;
  email: string;
  manufacturer: string;
  model: string;
  androidVersion: string;
  connectedAt: number;
  roomCode?: string;
  messageChain?: Promise<void>;
}
