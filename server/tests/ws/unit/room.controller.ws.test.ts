import { describe, expect, test, mock } from "bun:test";
import { RoomController } from "../../../src/features/room/room.controller";
import type { RoomService } from "../../../src/features/room/room.service";
import type { PublicIncident } from "../../../src/features/sos/sos.repository";
import { mockServerWebSocket } from "../../helpers/mock-websocket";

function makeService(overrides: Partial<Record<keyof RoomService, unknown>> = {}) {
  return {
    createRoom: mock(async () => ({
      code: "ABC123",
      masterId: "user-1",
      isSharing: false,
      shakeThreshold: 3.5,
      locationIntervalMs: 10_000,
      closedAt: null,
    })),
    joinRoom: mock(async () => "ok" as const),
    leaveRoom: mock(async () => null),
    detachSocket: mock(async () => null),
    rebindMembership: mock(async () => null),
    setSharing: mock(async () => { }),
    isClosed: (room: { closedAt: Date | null }) => room.closedAt != null,
    getRoom: mock(async () => ({
      code: "ABC123",
      masterId: "user-1",
      isSharing: false,
      shakeThreshold: 3.5,
      locationIntervalMs: 10_000,
      closedAt: null,
      createdAt: new Date(),
    })),
    updateLastLocation: mock(() => { }),
    getLastLocation: mock(() => undefined),
    getSockets: mock(() => undefined),
    broadcastToRoom: mock(() => { }),
    roomStatePayload: mock(
      (
        code: string,
        masterId: string,
        isSharing: boolean,
        shakeThreshold = 3.5,
        locationIntervalMs = 10_000
      ) => ({
        code,
        masterId,
        isSharing,
        shakeThreshold,
        locationIntervalMs,
        members: [],
      })
    ),
    ...overrides,
  } as unknown as RoomService;
}

function makeIncidents(incidents: PublicIncident[] = []) {
  return { listPublicByRoom: mock(async () => incidents) };
}

function makeController(service: RoomService = makeService(), sos = makeIncidents()) {
  return new RoomController(service, sos);
}

describe("RoomController.createViaWs", () => {
  test("when not in room: creates and returns room_state", async () => {
    const service = makeService();
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ userId: "user-1", roomCode: undefined });
    const msg = await controller.createViaWs(ws);
    expect(msg.type).toBe("room_state");
    expect((msg.payload as { code: string }).code).toBe("ABC123");
    expect((service as any).leaveRoom).not.toHaveBeenCalled();
    expect((service as any).createRoom).toHaveBeenCalledTimes(1);
  });

  test("when already in a room: leaves first then creates", async () => {
    const service = makeService();
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "OLD123" });
    await controller.createViaWs(ws);
    expect((service as any).leaveRoom).toHaveBeenCalledTimes(1);
    expect((service as any).createRoom).toHaveBeenCalledTimes(1);
  });

  test("attaches this-Room incidents on room_state without raw axes", async () => {
    const incidents = [
      {
        incidentId: "incident-1",
        userId: "user-2",
        name: "Kartika Widyasari",
        magnitudeG: 4.3,
        location: { latitude: -6.2, longitude: 106.8, accuracy: 8 },
        triggeredAt: "2026-08-25T07:02:00.000Z",
      },
    ];
    const sos = makeIncidents(incidents);
    const controller = makeController(makeService(), sos);
    const { ws } = mockServerWebSocket({ userId: "user-1", roomCode: undefined });
    const msg = await controller.createViaWs(ws);
    const payload = msg.payload as { incidents: unknown[] };
    expect(payload.incidents).toEqual(incidents);
    expect(JSON.stringify(payload)).not.toContain('"x"');
    expect(JSON.stringify(payload)).not.toContain('"y"');
    expect(JSON.stringify(payload)).not.toContain('"z"');
    expect(sos.listPublicByRoom).toHaveBeenCalledWith("ABC123");
  });
});

describe("RoomController.joinViaWs", () => {
  test("returns success:false when service refuses", async () => {
    const service = makeService({ joinRoom: mock(async () => "not_found" as const) });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket();
    const result = await controller.joinViaWs(ws, "ZZZZZZ");
    expect(result.success).toBe(false);
    expect(result.error).toBe("not_found");
    expect(result.messages).toBeUndefined();
  });

  test("returns closed when room is soft-closed", async () => {
    const service = makeService({ joinRoom: mock(async () => "closed" as const) });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket();
    const result = await controller.joinViaWs(ws, "OLD123");
    expect(result.success).toBe(false);
    expect(result.error).toBe("closed");
  });

  test("returns 2 messages on success: room_state + user_joined", async () => {
    const service = makeService();
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ userId: "user-1", name: "Alice", email: "a@a.com" });
    const result = await controller.joinViaWs(ws, "ABC123");
    expect(result.success).toBe(true);
    expect(result.messages).toHaveLength(2);
    expect(result.messages?.[0]?.type).toBe("room_state");
    expect(result.messages?.[1]?.type).toBe("user_joined");
    expect(result.messages?.[1]?.payload).toEqual({
      userId: "user-1",
      name: "Alice",
      email: "a@a.com",
    });
  });

  test("leaves prior room before joining new one", async () => {
    const service = makeService();
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "OLD123" });
    await controller.joinViaWs(ws, "NEW123");
    expect((service as any).leaveRoom).toHaveBeenCalledTimes(1);
  });

  test("does not leave when joining the same Room after rebind", async () => {
    const service = makeService();
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "ABC123" });
    await controller.joinViaWs(ws, "ABC123");
    expect((service as any).leaveRoom).not.toHaveBeenCalled();
    expect((service as any).joinRoom).toHaveBeenCalledWith("ABC123", ws);
  });
});

describe("RoomController.leaveViaWs", () => {
  test("returns empty leave when not in any room", async () => {
    const controller = makeController(makeService());
    const { ws } = mockServerWebSocket({ roomCode: undefined });
    const result = await controller.leaveViaWs(ws);
    expect(result.leaveMessage).toBeNull();
    expect(result.softClosed).toBe(false);
  });

  test("returns room_state with payload when members remain", async () => {
    const service = makeService({
      leaveRoom: mock(async () => ({
        softClosed: false,
        code: "ABC123",
        masterId: "user-2",
        isSharing: false,
        shakeThreshold: 3.5,
        locationIntervalMs: 10_000,
        members: new Map([["user-2", {}]]),
      })),
    });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "ABC123" });
    const result = await controller.leaveViaWs(ws);
    expect(result.leaveMessage?.type).toBe("room_state");
    expect((result.leaveMessage?.payload as { masterId: string }).masterId).toBe("user-2");
  });

  test("soft-closes when room emptied or Host leaves", async () => {
    const remaining = mockServerWebSocket({ userId: "user-2" }).ws;
    const service = makeService({
      leaveRoom: mock(async () => ({
        softClosed: true,
        code: "ABC123",
        remaining: [remaining],
      })),
    });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "ABC123" });
    const result = await controller.leaveViaWs(ws);
    expect(result.softClosed).toBe(true);
    expect(result.leaveMessage?.payload).toBeNull();
    expect(result.remaining).toEqual([remaining]);
  });
});

describe("RoomController.startViaWs / stopViaWs", () => {
  test("startViaWs returns null when not in a room", async () => {
    const controller = makeController(makeService());
    const { ws } = mockServerWebSocket({ roomCode: undefined });
    expect(await controller.startViaWs(ws)).toBeNull();
  });

  test("startViaWs returns null when room not found", async () => {
    const service = makeService({ getRoom: mock(async () => undefined) });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "ABC123" });
    expect(await controller.startViaWs(ws)).toBeNull();
  });

  test("startViaWs returns null when caller is not host", async () => {
    const service = makeService({
      getRoom: mock(async () => ({
        code: "ABC123",
        masterId: "OTHER",
        isSharing: false,
        shakeThreshold: 3.5,
        locationIntervalMs: 10_000,
        closedAt: null,
        createdAt: new Date(),
      })),
    });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "ABC123", userId: "user-1" });
    expect(await controller.startViaWs(ws)).toBeNull();
  });

  test("startViaWs as host returns room_start and flips sharing", async () => {
    const incidents = [
      {
        incidentId: "incident-1",
        userId: "user-2",
        name: "Kartika Widyasari",
        magnitudeG: 4.3,
        location: { latitude: -6.2, longitude: 106.8 },
        triggeredAt: "2026-08-25T07:02:00.000Z",
      },
    ];
    const service = makeService();
    const sos = makeIncidents(incidents);
    const controller = makeController(service, sos);
    const { ws } = mockServerWebSocket({ roomCode: "ABC123", userId: "user-1" });
    const msg = await controller.startViaWs(ws);
    expect(msg?.type).toBe("room_start");
    expect((msg?.payload as { isSharing: boolean }).isSharing).toBe(true);
    expect((msg?.payload as { incidents: unknown[] }).incidents).toEqual(incidents);
    expect((service as any).setSharing).toHaveBeenCalledWith("ABC123", true);
    expect(sos.listPublicByRoom).toHaveBeenCalledWith("ABC123");
  });

  test("stopViaWs as host returns room_stop and only flips sharing", async () => {
    const service = makeService();
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "ABC123", userId: "user-1" });
    const result = await controller.stopViaWs(ws);
    expect(result?.type).toBe("room_stop");
    expect((service as any).setSharing).toHaveBeenCalledWith("ABC123", false);
  });

  test("stopViaWs returns null when not in a room", async () => {
    const controller = makeController(makeService());
    const { ws } = mockServerWebSocket({ roomCode: undefined });
    expect(await controller.stopViaWs(ws)).toBeNull();
  });

  test("stopViaWs returns null when not host", async () => {
    const service = makeService({
      getRoom: mock(async () => ({
        code: "ABC123",
        masterId: "OTHER",
        isSharing: true,
        shakeThreshold: 3.5,
        locationIntervalMs: 10_000,
        closedAt: null,
        createdAt: new Date(),
      })),
    });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "ABC123", userId: "user-1" });
    const result = await controller.stopViaWs(ws);
    expect(result).toBeNull();
  });
});

describe("RoomController.disconnectViaWs", () => {
  test("returns empty when not in room", async () => {
    const controller = makeController(makeService());
    const { ws } = mockServerWebSocket();
    const result = await controller.disconnectViaWs(ws);
    expect(result).toEqual({});
  });

  test("detaches instead of leaving and never soft-closes", async () => {
    const service = makeService({
      detachSocket: mock(async () => ({
        code: "ABC123",
        masterId: "user-1",
        isSharing: true,
        shakeThreshold: 3.5,
        locationIntervalMs: 10_000,
        members: new Map(),
      })),
    });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ roomCode: "ABC123", userId: "user-1", name: "Alice" });
    const result = await controller.disconnectViaWs(ws);
    expect((service as any).detachSocket).toHaveBeenCalledTimes(1);
    expect((service as any).leaveRoom).not.toHaveBeenCalled();
    expect(result.code).toBe("ABC123");
    expect(result.messages).toHaveLength(2);
    expect(result.messages?.[0]?.type).toBe("room_state");
    expect(result.messages?.[1]?.type).toBe("user_offline");
    expect(result.messages?.[1]?.payload).toEqual({ userId: "user-1", name: "Alice" });
  });
});

describe("RoomController.rebindViaWs", () => {
  test("returns null when no open membership exists", async () => {
    const controller = makeController(makeService());
    const { ws } = mockServerWebSocket({ roomCode: undefined });
    expect(await controller.rebindViaWs(ws)).toBeNull();
  });

  test("returns room_state and user_joined when membership is restored", async () => {
    const service = makeService({
      rebindMembership: mock(async (ws: { data: { roomCode?: string } }) => {
        ws.data.roomCode = "ABC123";
        return {
          code: "ABC123",
          masterId: "user-1",
          isSharing: true,
          shakeThreshold: 3.5,
          locationIntervalMs: 10_000,
          closedAt: null,
          createdAt: new Date(),
        };
      }),
    });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ userId: "user-1", name: "Alice", email: "a@a.com" });
    const result = await controller.rebindViaWs(ws);
    expect(result?.code).toBe("ABC123");
    expect(result?.selfMessage.type).toBe("room_state");
    expect((result?.selfMessage.payload as { isSharing: boolean }).isSharing).toBe(true);
    expect(result?.broadcastMessage).toEqual({
      type: "user_joined",
      payload: { userId: "user-1", name: "Alice", email: "a@a.com" },
      timestamp: expect.any(Number),
    });
  });

  test("stopViaWs succeeds after rebind restores roomCode", async () => {
    const service = makeService({
      rebindMembership: mock(async (ws: { data: { userId: string; roomCode?: string } }) => {
        ws.data.roomCode = "ABC123";
        return {
          code: "ABC123",
          masterId: "user-1",
          isSharing: true,
          shakeThreshold: 3.5,
          locationIntervalMs: 10_000,
          closedAt: null,
          createdAt: new Date(),
        };
      }),
      getRoom: mock(async () => ({
        code: "ABC123",
        masterId: "user-1",
        isSharing: true,
        shakeThreshold: 3.5,
        locationIntervalMs: 10_000,
        closedAt: null,
        createdAt: new Date(),
      })),
    });
    const controller = makeController(service);
    const { ws } = mockServerWebSocket({ userId: "user-1", roomCode: undefined });
    await controller.rebindViaWs(ws);
    const result = await controller.stopViaWs(ws);
    expect(result?.type).toBe("room_stop");
  });
});
