import { describe, expect, test } from "bun:test";
import { WebSocketManager, short, sendError } from "../../../src/features/ws/ws.manager";
import { mockServerWebSocket } from "../../helpers/mock-websocket";

describe("WebSocketManager", () => {
  test("add + get + count + remove", () => {
    const m = new WebSocketManager();
    const { ws } = mockServerWebSocket({ userId: "u1" });
    m.addConnection("u1", ws);
    expect(m.getConnection("u1")).toBe(ws);
    expect(m.getConnectionCount()).toBe(1);
    m.removeConnection("u1");
    expect(m.getConnection("u1")).toBeUndefined();
    expect(m.getConnectionCount()).toBe(0);
  });

  test("getConnections returns the underlying map", () => {
    const m = new WebSocketManager();
    const { ws } = mockServerWebSocket({ userId: "u1" });
    m.addConnection("u1", ws);
    const conns = m.getConnections();
    expect(conns.size).toBe(1);
    expect(conns.get("u1")).toBe(ws);
  });

  test("clearConnections empties the map", () => {
    const m = new WebSocketManager();
    const { ws: a } = mockServerWebSocket({ userId: "u1" });
    const { ws: b } = mockServerWebSocket({ userId: "u2" });
    m.addConnection("u1", a);
    m.addConnection("u2", b);
    m.clearConnections();
    expect(m.getConnectionCount()).toBe(0);
  });

  test("addConnection replaces existing entry for same userId", () => {
    const m = new WebSocketManager();
    const { ws: a } = mockServerWebSocket({ userId: "u1" });
    const { ws: b } = mockServerWebSocket({ userId: "u1" });
    m.addConnection("u1", a);
    m.addConnection("u1", b);
    expect(m.getConnection("u1")).toBe(b);
    expect(m.getConnectionCount()).toBe(1);
  });
});

describe("short", () => {
  test("returns first 8 chars", () => {
    expect(short("0123456789abcdef")).toBe("01234567");
  });

  test("handles short strings", () => {
    expect(short("abc")).toBe("abc");
  });
});

describe("sendError", () => {
  test("sends room_error envelope to ws", () => {
    const handle = mockServerWebSocket();
    sendError(handle.ws, "boom");
    const msgs = handle.parseSent();
    expect(msgs).toHaveLength(1);
    expect(msgs[0]?.type).toBe("room_error");
    expect(msgs[0]?.payload).toEqual({ message: "boom" });
    expect(typeof msgs[0]?.timestamp).toBe("number");
  });
});
