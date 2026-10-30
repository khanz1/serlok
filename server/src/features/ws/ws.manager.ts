import type { ServerWebSocket } from "bun";
import type { WsData } from "./ws.types";

export class WebSocketManager {
  private connections: Map<string, ServerWebSocket<WsData>>;

  constructor() {
    this.connections = new Map();
  }

  addConnection(userId: string, ws: ServerWebSocket<WsData>): void {
    this.connections.set(userId, ws);
  }

  removeConnection(userId: string): void {
    this.connections.delete(userId);
  }

  getConnection(userId: string): ServerWebSocket<WsData> | undefined {
    return this.connections.get(userId);
  }

  getConnections(): Map<string, ServerWebSocket<WsData>> {
    return this.connections;
  }

  getConnectionCount(): number {
    return this.connections.size;
  }

  clearConnections(): void {
    this.connections.clear();
  }

  static short(userId: string): string {
    return userId.slice(0, 8);
  }

  static sendError(ws: ServerWebSocket<WsData>, message: string): void {
    ws.send(JSON.stringify({ type: "room_error", payload: { message }, timestamp: Date.now() }));
  }
}

export const wsManager = new WebSocketManager();

// Re-export utility functions for backward compatibility
export const short = WebSocketManager.short;
export const sendError = WebSocketManager.sendError;
