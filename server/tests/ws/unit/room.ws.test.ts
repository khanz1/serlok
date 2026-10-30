import { describe, expect, test, mock } from "bun:test";
import { createRoomWsHandler } from "../../../src/features/ws/room.ws";
import { LocationThrottle } from "../../../src/features/ws/throttle";
import { mockServerWebSocket } from "../../helpers/mock-websocket";

function makeDeps(
  overrides: {
    controller?: Record<string, any>;
    service?: Record<string, any>;
    now?: () => number;
  } = {}
) {
  const controller = { ...makeController(), ...(overrides.controller ?? {}) };
  const service = { ...makeService(), ...(overrides.service ?? {}) };
  return {
    controller,
    service,
    handler: createRoomWsHandler({
      roomController: controller as any,
      roomService: service as any,
      throttle: new LocationThrottle(),
      now: overrides.now ?? (() => 1_000_000),
    }),
  };
}

function makeController() {
  return {
    createViaWs: mock(async () => ({
      type: "room_state",
      payload: { code: "ABC123" },
      timestamp: 1,
    })),
    joinViaWs: mock(async (_ws: unknown, _code: string) => ({
      success: true as const,
      messages: [
        { type: "room_state", payload: { code: "ABC123" }, timestamp: 1 },
        { type: "user_joined", payload: { userId: "user-1" }, timestamp: 1 },
      ],
    })),
    leaveViaWs: mock(async () => ({
      leaveMessage: { type: "room_state", payload: null, timestamp: 1 },
      remaining: [] as unknown[],
      softClosed: false,
      code: "ABC123",
    })),
    startViaWs: mock(async () => ({
      type: "room_start",
      payload: { isSharing: true },
      timestamp: 1,
    })),
    stopViaWs: mock(async () => ({
      type: "room_stop",
      payload: { isSharing: false },
      timestamp: 1,
    })),
    disconnectViaWs: mock(async () => ({}) as { code?: string; messages?: unknown[] }),
    rebindViaWs: mock(async () => null as null),
  };
}

function makeService() {
  return {
    broadcastToRoom: mock((_code: string, _payload: string, _exclude?: string) => { }),
    updateLastLocation: mock(
      (_roomCode: string, _userId: string, _lat: number, _lng: number) => { }
    ),
    getRoom: mock(
      async (_code: string) =>
        ({
          code: _code,
          masterId: "user-1",
          isSharing: true,
          shakeThreshold: 3.5,
          locationIntervalMs: 10_000,
          closedAt: null,
          createdAt: new Date(),
        }) as any
    ),
    setShakeThreshold: mock(async (_code: string, _shakeThreshold: number) => { }),
    setSettings: mock(async () => { }),
    isValidShakeThreshold: (v: number) => [2.5, 3.5, 4.5].includes(v),
    isValidLocationIntervalMs: (v: number) => [2000, 5000, 10000, 30000, 60000].includes(v),
    isClosed: (room: { closedAt: Date | null }) => room.closedAt != null,
  };
}

describe("createRoomWsHandler", () => {
  describe("location_update", () => {
    test("with roomCode broadcasts and updates last location", async () => {
      const { handler, service } = makeDeps();
      const { ws } = mockServerWebSocket({ userId: "user-1", roomCode: "ABC123" });
      const handled = await handler.handleRoomMessage(ws, "location_update", {
        latitude: 1.23,
        longitude: 4.56,
        accuracy: 5,
        speed: 10,
      });
      expect(handled).toBe(true);
      expect(service.broadcastToRoom).toHaveBeenCalledTimes(1);
      const [code, payload, exclude] = (service.broadcastToRoom as any).mock.calls[0];
      expect(code).toBe("ABC123");
      expect(exclude).toBe("user-1");
      const msg = JSON.parse(payload);
      expect(msg.type).toBe("location_update");
      expect(msg.payload).toEqual({
        userId: "user-1",
        latitude: 1.23,
        longitude: 4.56,
        accuracy: 5,
        speed: 10,
      });
      expect(service.updateLastLocation).toHaveBeenCalledWith("ABC123", "user-1", 1.23, 4.56, 5);
    });

    test("ignores battery on location payload", async () => {
      const { handler, service } = makeDeps();
      const { ws } = mockServerWebSocket({ roomCode: "ABC123" });
      await handler.handleRoomMessage(ws, "location_update", {
        latitude: 0,
        longitude: 0,
        battery: 80,
      });
      const [, payload] = (service.broadcastToRoom as any).mock.calls[0];
      const msg = JSON.parse(payload);
      expect(msg.payload.battery).toBeUndefined();
    });

    test("without roomCode skips broadcast but still records throttle", async () => {
      const { handler, service } = makeDeps();
      const { ws } = mockServerWebSocket({ roomCode: undefined });
      const handled = await handler.handleRoomMessage(ws, "location_update", {
        latitude: 0,
        longitude: 0,
      });
      expect(handled).toBe(true);
      expect(service.broadcastToRoom).not.toHaveBeenCalled();
      expect(service.updateLastLocation).not.toHaveBeenCalled();
    });
  });

  describe("battery_update", () => {
    test("is not a handled room type", async () => {
      const { handler, service } = makeDeps();
      const { ws } = mockServerWebSocket({ userId: "user-1", roomCode: "ABC123" });
      expect(await handler.handleRoomMessage(ws, "battery_update", { battery: 42 })).toBe(false);
      expect(service.broadcastToRoom).not.toHaveBeenCalled();
    });
  });

  describe("room_create", () => {
    test("calls controller and sends result back", async () => {
      const { handler, controller } = makeDeps();
      const handle = mockServerWebSocket();
      await handler.handleRoomMessage(handle.ws, "room_create", {});
      expect(controller.createViaWs).toHaveBeenCalledTimes(1);
      const msgs = handle.parseSent();
      expect(msgs).toHaveLength(1);
      expect(msgs[0]?.type).toBe("room_state");
    });
  });

  describe("room_join", () => {
    test("missing code sends room_error", async () => {
      const { handler, controller } = makeDeps();
      const handle = mockServerWebSocket();
      await handler.handleRoomMessage(handle.ws, "room_join", {});
      expect(controller.joinViaWs).not.toHaveBeenCalled();
      const msgs = handle.parseSent();
      expect(msgs[0]?.type).toBe("room_error");
      expect((msgs[0]?.payload as { message: string }).message).toMatch(/Missing/);
    });

    test("not-found result sends room_error", async () => {
      const { handler } = makeDeps({
        controller: { joinViaWs: mock(async () => ({ success: false as const })) },
      });
      const handle = mockServerWebSocket();
      await handler.handleRoomMessage(handle.ws, "room_join", { code: "ZZZZZZ" });
      const msgs = handle.parseSent();
      expect(msgs[0]?.type).toBe("room_error");
      expect((msgs[0]?.payload as { message: string }).message).toContain("ZZZZZZ");
    });

    test("success sends room_state to joiner and broadcasts both messages", async () => {
      const { handler, service } = makeDeps();
      const handle = mockServerWebSocket({ userId: "user-1" });
      await handler.handleRoomMessage(handle.ws, "room_join", { code: "abc123" });
      const sent = handle.parseSent();
      expect(sent).toHaveLength(1);
      expect(sent[0]?.type).toBe("room_state");
      expect(service.broadcastToRoom).toHaveBeenCalledTimes(2);

      // 1st broadcast: room_state to all (no exclude)
      const [code1, payload1, exclude1] = (service.broadcastToRoom as any).mock.calls[0];
      expect(code1).toBe("ABC123");
      expect(exclude1).toBeUndefined();
      expect(JSON.parse(payload1).type).toBe("room_state");

      // 2nd broadcast: user_joined to others (exclude self)
      const [code2, payload2, exclude2] = (service.broadcastToRoom as any).mock.calls[1];
      expect(code2).toBe("ABC123");
      expect(exclude2).toBe("user-1");
      expect(JSON.parse(payload2).type).toBe("user_joined");
    });

    test("uppercases the code before calling controller", async () => {
      const { handler, controller } = makeDeps();
      const handle = mockServerWebSocket();
      await handler.handleRoomMessage(handle.ws, "room_join", { code: "abcdef" });
      expect(controller.joinViaWs).toHaveBeenCalledWith(handle.ws, "ABCDEF");
    });
  });

  describe("room_leave", () => {
    test("sends controller-returned message back to client", async () => {
      const { handler } = makeDeps();
      const handle = mockServerWebSocket({ roomCode: "ABC123" });
      await handler.handleRoomMessage(handle.ws, "room_leave", {});
      const msgs = handle.parseSent();
      expect(msgs[0]?.type).toBe("room_state");
    });

    test("sends nothing when controller returns empty leave", async () => {
      const { handler } = makeDeps({
        controller: {
          leaveViaWs: mock(async () => ({
            leaveMessage: null,
            remaining: [],
            softClosed: false,
            code: null,
          })),
        },
      });
      const handle = mockServerWebSocket();
      await handler.handleRoomMessage(handle.ws, "room_leave", {});
      expect(handle.sent).toHaveLength(0);
    });
  });

  describe("room_start", () => {
    test("master path broadcasts room_start to room", async () => {
      const { handler, service } = makeDeps();
      const handle = mockServerWebSocket({ roomCode: "ABC123" });
      await handler.handleRoomMessage(handle.ws, "room_start", {});
      expect(service.broadcastToRoom).toHaveBeenCalledTimes(1);
      const [code, payload] = (service.broadcastToRoom as any).mock.calls[0];
      expect(code).toBe("ABC123");
      expect(JSON.parse(payload).type).toBe("room_start");
    });

    test("non-master path sends room_error to caller", async () => {
      const { handler, service } = makeDeps({
        controller: { startViaWs: mock(async () => null) },
      });
      const handle = mockServerWebSocket();
      await handler.handleRoomMessage(handle.ws, "room_start", {});
      expect(service.broadcastToRoom).not.toHaveBeenCalled();
      const msgs = handle.parseSent();
      expect(msgs[0]?.type).toBe("room_error");
    });
  });

  describe("room_stop", () => {
    test("master path broadcasts room_stop to room", async () => {
      const { handler, service } = makeDeps();
      const handle = mockServerWebSocket({ roomCode: "ABC123" });
      await handler.handleRoomMessage(handle.ws, "room_stop", {});
      const [, payload] = (service.broadcastToRoom as any).mock.calls[0];
      expect(JSON.parse(payload).type).toBe("room_stop");
    });

    test("non-master path sends room_error", async () => {
      const { handler } = makeDeps({
        controller: {
          stopViaWs: mock(async () => null),
        },
      });
      const handle = mockServerWebSocket();
      await handler.handleRoomMessage(handle.ws, "room_stop", {});
      const msgs = handle.parseSent();
      expect(msgs[0]?.type).toBe("room_error");
    });
  });

  describe("room_update_settings", () => {
    test("host updates settings when session idle and broadcasts", async () => {
      const { handler, service } = makeDeps({
        service: {
          getRoom: mock(
            async () =>
              ({
                code: "ABC123",
                masterId: "user-1",
                isSharing: false,
                shakeThreshold: 3.5,
                locationIntervalMs: 10_000,
                closedAt: null,
                createdAt: new Date(),
              }) as any
          ),
        },
      });
      const handle = mockServerWebSocket({ userId: "user-1", roomCode: "ABC123" });

      await handler.handleRoomMessage(handle.ws, "room_update_settings", {
        shakeThreshold: 4.5,
        locationIntervalMs: 5_000,
      });

      expect(service.setSettings).toHaveBeenCalledWith("ABC123", 4.5, 5_000);
      expect(service.broadcastToRoom).toHaveBeenCalledTimes(1);
      const [code, payload] = (service.broadcastToRoom as any).mock.calls[0];
      expect(code).toBe("ABC123");
      expect(JSON.parse(payload)).toMatchObject({
        type: "room_settings",
        payload: { shakeThreshold: 4.5, locationIntervalMs: 5_000 },
      });
    });

    test("rejects non-host settings updates", async () => {
      const { handler, service } = makeDeps({
        service: {
          getRoom: mock(
            async () =>
              ({
                code: "ABC123",
                masterId: "other-user",
                isSharing: false,
                shakeThreshold: 3.5,
                locationIntervalMs: 10_000,
                closedAt: null,
                createdAt: new Date(),
              }) as any
          ),
        },
      });
      const handle = mockServerWebSocket({ userId: "user-1", roomCode: "ABC123" });

      await handler.handleRoomMessage(handle.ws, "room_update_settings", {
        shakeThreshold: 4.5,
        locationIntervalMs: 5_000,
      });

      expect(service.setSettings).not.toHaveBeenCalled();
      expect(handle.parseSent()[0]?.type).toBe("room_error");
    });
  });

  describe("unknown type", () => {
    test("returns false", async () => {
      const { handler } = makeDeps();
      const handle = mockServerWebSocket();
      const result = await handler.handleRoomMessage(handle.ws, "totally_unknown", {});
      expect(result).toBe(false);
    });
  });

  describe("handleRoomDisconnect", () => {
    test("returns undefined when controller has no code", async () => {
      const { handler } = makeDeps();
      const handle = mockServerWebSocket();
      const result = await handler.handleRoomDisconnect(handle.ws);
      expect(result).toBeUndefined();
    });

    test("broadcasts each disconnect message to room", async () => {
      const { handler, service } = makeDeps({
        controller: {
          disconnectViaWs: mock(async () => ({
            code: "ABC123",
            messages: [
              { type: "room_state", payload: {}, timestamp: 1 },
              { type: "user_offline", payload: {}, timestamp: 1 },
            ],
          })),
        },
      });
      const handle = mockServerWebSocket();
      const result = await handler.handleRoomDisconnect(handle.ws);
      expect(result).toEqual({ code: "ABC123" });
      expect(service.broadcastToRoom).toHaveBeenCalledTimes(2);
    });

    test("does not send room_state null when Host disconnects", async () => {
      const { handler, service } = makeDeps({
        controller: {
          disconnectViaWs: mock(async () => ({
            code: "ABC123",
            messages: [
              { type: "room_state", payload: { code: "ABC123", isSharing: true }, timestamp: 1 },
              { type: "user_offline", payload: { userId: "user-1" }, timestamp: 1 },
            ],
          })),
        },
      });
      const handle = mockServerWebSocket();
      const result = await handler.handleRoomDisconnect(handle.ws);
      expect(result).toEqual({ code: "ABC123" });
      expect(service.broadcastToRoom).toHaveBeenCalledTimes(2);
    });
  });

  describe("handleRoomReconnect", () => {
    test("sends room_state to the rebound socket and user_joined to others", async () => {
      const { handler, service } = makeDeps({
        controller: {
          rebindViaWs: mock(async () => ({
            code: "ABC123",
            selfMessage: {
              type: "room_state",
              payload: { code: "ABC123", isSharing: true },
              timestamp: 1,
            },
            broadcastMessage: {
              type: "user_joined",
              payload: { userId: "user-1", name: "Alice", email: "a@a.com" },
              timestamp: 1,
            },
          })),
        },
      });
      const handle = mockServerWebSocket({ userId: "user-1" });
      await handler.handleRoomReconnect(handle.ws);
      expect(handle.parseSent()[0]).toMatchObject({
        type: "room_state",
        payload: { code: "ABC123", isSharing: true },
      });
      expect(service.broadcastToRoom).toHaveBeenCalledTimes(1);
      const [code, payload, exclude] = (service.broadcastToRoom as any).mock.calls[0];
      expect(code).toBe("ABC123");
      expect(exclude).toBe("user-1");
      expect(JSON.parse(payload).type).toBe("user_joined");
    });

    test("sends nothing when there is no membership to restore", async () => {
      const { handler, service } = makeDeps();
      const handle = mockServerWebSocket();
      await handler.handleRoomReconnect(handle.ws);
      expect(handle.sent).toHaveLength(0);
      expect(service.broadcastToRoom).not.toHaveBeenCalled();
    });
  });

  describe("cleanupRoomDevice", () => {
    test("delegates to throttle", () => {
      const throttle = new LocationThrottle();
      const handler = createRoomWsHandler({
        roomController: makeController() as any,
        roomService: makeService() as any,
        throttle,
      });
      throttle.record("user-1", 0);
      handler.cleanupRoomDevice("user-1");
      // After cleanup, fresh entry — first call returns null again
      expect(throttle.record("user-1", 0)).toBeNull();
    });
  });
});
