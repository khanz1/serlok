// Central WebSocket module - similar to auth.module.ts pattern
// This is the single entry point for all WebSocket functionality

export { WebSocketManager, wsManager, short, sendError } from "./ws.manager";
export { elapsed } from "./ws.utils";
export { wsHandler } from "./ws.handler";
export type { WsData } from "./ws.types";

// Re-export handler functions for advanced use cases
export {
  handleRoomMessage,
  handleRoomDisconnect,
  handleRoomReconnect,
  cleanupRoomDevice,
} from "./room.ws";
export { handleSosMessage, cleanupSosDevice } from "./sos.ws";
