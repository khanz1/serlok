import { describe, expect, test, beforeEach, afterAll } from "bun:test";
import { createWsHandler } from "../../../src/features/ws/ws.handler";
import { wsManager } from "../../../src/features/ws/ws.manager";
import { mockServerWebSocket } from "../../helpers/mock-websocket";

const wsHandler = createWsHandler(async () => { });

// Wait for fire-and-forget async work scheduled inside message/close handlers
const flush = () => new Promise((resolve) => setImmediate(resolve));

describe("wsHandler.open", () => {
  beforeEach(() => wsManager.clearConnections());
  afterAll(() => wsManager.clearConnections());

  test("registers connection in wsManager", () => {
    const { ws } = mockServerWebSocket({ userId: "user-1" });
    wsHandler.open(ws);
    expect(wsManager.getConnection("user-1")).toBe(ws);
    expect(wsManager.getConnectionCount()).toBe(1);
  });

  test("closes prior connection for same userId", () => {
    const a = mockServerWebSocket({ userId: "user-1" });
    const b = mockServerWebSocket({ userId: "user-1" });
    wsHandler.open(a.ws);
    wsHandler.open(b.ws);
    expect(a.closed).toHaveLength(1);
    expect(a.closed[0]?.reason).toMatch(/Replaced/);
    expect(wsManager.getConnection("user-1")).toBe(b.ws);
  });

  test("does not close itself if same ws is reopened", () => {
    const a = mockServerWebSocket({ userId: "user-1" });
    wsHandler.open(a.ws);
    wsHandler.open(a.ws);
    expect(a.closed).toHaveLength(0);
  });

  test("reconnect completes before queued messages are processed", async () => {
    let release = () => { };
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    let reconnectStarted = false;
    const handler = createWsHandler(async () => {
      reconnectStarted = true;
      await gate;
    });
    const handle = mockServerWebSocket({ userId: "user-chain" });
    handler.open(handle.ws);
    handler.message(handle.ws, JSON.stringify({ type: "ping", timestamp: Date.now() }));
    await flush();
    expect(reconnectStarted).toBe(true);
    expect(handle.sent).toHaveLength(0);
    release();
    await flush();
    expect(handle.parseSent()[0]?.type).toBe("pong");
  });
});

describe("wsHandler.message", () => {
  beforeEach(() => wsManager.clearConnections());

  test("ping → pong reply", async () => {
    const handle = mockServerWebSocket({ userId: "user-1" });
    wsHandler.message(handle.ws, JSON.stringify({ type: "ping", timestamp: Date.now() }));
    await flush();
    const msgs = handle.parseSent();
    expect(msgs).toHaveLength(1);
    expect(msgs[0]?.type).toBe("pong");
    expect(typeof msgs[0]?.timestamp).toBe("number");
  });

  test("invalid JSON is swallowed (no throw, no send)", async () => {
    const handle = mockServerWebSocket();
    wsHandler.message(handle.ws, "{not json");
    await flush();
    expect(handle.sent).toHaveLength(0);
  });

  test("schema-invalid message is swallowed", async () => {
    const handle = mockServerWebSocket();
    wsHandler.message(handle.ws, JSON.stringify({ type: "totally_unknown" }));
    await flush();
    expect(handle.sent).toHaveLength(0);
  });

  test("rejects binary messages", async () => {
    const handle = mockServerWebSocket({ userId: "user-1" });
    const buf = Buffer.from(JSON.stringify({ type: "ping", timestamp: 1 }));
    wsHandler.message(handle.ws, buf);
    await flush();
    expect(handle.sent).toHaveLength(0);
    expect(handle.closed).toEqual([{ code: 1003, reason: "Binary messages are not supported" }]);
  });
});

describe("wsHandler.close", () => {
  beforeEach(() => wsManager.clearConnections());

  test("removes connection and only same ws", async () => {
    const handle = mockServerWebSocket({ userId: "user-1", connectedAt: Date.now() - 1000 });
    wsManager.addConnection("user-1", handle.ws);
    wsHandler.close(handle.ws, 1000, "");
    await flush();
    expect(wsManager.getConnection("user-1")).toBeUndefined();
  });

  test("does not remove a different connection registered for same user", async () => {
    const a = mockServerWebSocket({ userId: "user-1" });
    const b = mockServerWebSocket({ userId: "user-1" });
    wsManager.addConnection("user-1", b.ws); // newer one wins
    wsHandler.close(a.ws, 1000, ""); // older closes — should not remove b
    await flush();
    expect(wsManager.getConnection("user-1")).toBe(b.ws);
  });
});

describe("wsHandler.drain", () => {
  test("is a no-op", () => {
    const { ws } = mockServerWebSocket();
    expect(() => wsHandler.drain(ws)).not.toThrow();
  });
});
