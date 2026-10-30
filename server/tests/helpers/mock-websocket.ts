import type { ServerWebSocket } from "bun";
import type { WsData } from "../../src/features/ws/ws.types";

export interface MockWsHandle {
  ws: ServerWebSocket<WsData>;
  sent: string[];
  closed: { code: number; reason: string }[];
  parseSent: () => Array<{ type: string; payload?: unknown; timestamp?: number }>;
  reset: () => void;
}

export function mockServerWebSocket(data: Partial<WsData> = {}): MockWsHandle {
  const sent: string[] = [];
  const closed: { code: number; reason: string }[] = [];

  const fullData: WsData = {
    userId: data.userId ?? "user-1",
    deviceInstanceId: data.deviceInstanceId ?? "device-1",
    name: data.name ?? "Test User",
    email: data.email ?? "test@example.com",
    manufacturer: data.manufacturer ?? "TestCo",
    model: data.model ?? "TestModel",
    androidVersion: data.androidVersion ?? "13",
    connectedAt: data.connectedAt ?? Date.now(),
    roomCode: data.roomCode,
  };

  const ws = {
    data: fullData,
    readyState: 1,
    send: (msg: string | Buffer) => {
      sent.push(typeof msg === "string" ? msg : msg.toString());
      return 0;
    },
    close: (code = 1000, reason = "") => {
      closed.push({ code, reason });
    },
    subscribe: () => {},
    unsubscribe: () => {},
    publish: () => 0,
    cork: (cb: () => void) => cb(),
    binaryType: "uint8array" as const,
    isSubscribed: () => false,
    ping: () => 0,
    pong: () => 0,
    terminate: () => {},
    remoteAddress: "127.0.0.1",
  } as unknown as ServerWebSocket<WsData>;

  return {
    ws,
    sent,
    closed,
    parseSent: () =>
      sent.map((raw) => JSON.parse(raw) as { type: string; payload?: unknown; timestamp?: number }),
    reset: () => {
      sent.length = 0;
      closed.length = 0;
    },
  };
}
