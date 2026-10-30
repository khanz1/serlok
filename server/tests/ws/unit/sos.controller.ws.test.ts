import { describe, expect, mock, test } from "bun:test";
import { SosController } from "../../../src/features/sos/sos.controller";
import { AlertCooldown } from "../../../src/features/ws/throttle";
import { mockServerWebSocket } from "../../helpers/mock-websocket";

const EVENT_ID = "11111111-1111-4111-8111-111111111111";
const IDEMPOTENCY_ID = "22222222-2222-4222-8222-222222222222";
const SHAKE = { magnitudeG: 4.1, x: 40, y: 1, z: 2 };

function makeController(
  options: {
    isSharing?: boolean;
    shakeThreshold?: number;
    now?: () => number;
    location?: { latitude: number; longitude: number; accuracy?: number } | null;
  } = {}
) {
  const roomService = {
    getRoom: mock(async (code: string) => ({
      code,
      masterId: "owner-1",
      isSharing: options.isSharing ?? true,
      shakeThreshold: options.shakeThreshold ?? 3.5,
    })),
    broadcastToRoom: mock((_code: string, _payload: string) => {}),
    getLastLocation: mock(() =>
      options.location === null
        ? undefined
        : (options.location ?? { latitude: -6.2, longitude: 106.8, accuracy: 8 })
    ),
  };
  const sosRepository = {
    create: mock(async (data: Record<string, unknown>) => ({
      created: true,
      event: {
        ...data,
        id: EVENT_ID,
        createdAt: new Date(1_000_000),
        updatedAt: new Date(1_000_000),
      },
    })) as any,
  };
  const controller = new SosController({
    roomService,
    sosRepository,
    cooldown: new AlertCooldown(3_000),
    now: options.now ?? (() => 1_000_000),
    uuid: () => IDEMPOTENCY_ID,
  });
  return { controller, roomService, sosRepository };
}

function raw(timestamp = 1_000_000, idempotencyKey = IDEMPOTENCY_ID) {
  return JSON.stringify({ type: "sos_trigger", timestamp, idempotencyKey });
}

describe("SosController shake-only contract", () => {
  test("requires an active Safety Session and Room threshold", async () => {
    const inactive = makeController({ isSharing: false });
    const below = makeController({ shakeThreshold: 4.5 });
    const { ws } = mockServerWebSocket({ userId: "owner-1", roomCode: "ABC123" });

    await inactive.controller.handleSosTriggerViaWs(ws, SHAKE, raw());
    await below.controller.handleSosTriggerViaWs(ws, SHAKE, raw());

    expect(inactive.sosRepository.create).not.toHaveBeenCalled();
    expect(below.sosRepository.create).not.toHaveBeenCalled();
  });

  test("rejects legacy source and any incomplete payload", async () => {
    const { controller, sosRepository } = makeController();
    const handle = mockServerWebSocket({ userId: "owner-1", roomCode: "ABC123" });

    await controller.handleSosTriggerViaWs(handle.ws, { ...SHAKE, source: "manual" }, raw());
    await controller.handleSosTriggerViaWs(handle.ws, { magnitudeG: 4.1 }, raw());

    expect(sosRepository.create).not.toHaveBeenCalled();
    expect(handle.parseSent().map((message) => message.type)).toEqual(["room_error", "room_error"]);
  });

  test("rejects stale timestamp and malformed envelope idempotency", async () => {
    const { controller, sosRepository } = makeController();
    const handle = mockServerWebSocket({ userId: "owner-1", roomCode: "ABC123" });

    await controller.handleSosTriggerViaWs(handle.ws, SHAKE, raw(900_000));
    await controller.handleSosTriggerViaWs(handle.ws, SHAKE, raw(1_000_000, "bad-key"));

    expect(sosRepository.create).not.toHaveBeenCalled();
    expect(handle.parseSent()).toHaveLength(2);
  });

  test("stores and broadcasts only the shake contract", async () => {
    const { controller, roomService, sosRepository } = makeController();
    const sender = mockServerWebSocket({ userId: "owner-1", name: "Alice", roomCode: "ABC123" });

    await controller.handleSosTriggerViaWs(sender.ws, SHAKE, raw());

    expect((sosRepository.create as any).mock.calls[0][0]).toMatchObject({
      roomCode: "ABC123",
      userId: "owner-1",
      idempotencyKey: IDEMPOTENCY_ID,
      magnitude: 4.1,
      x: 40,
      y: 1,
      z: 2,
      latitude: -6.2,
      longitude: 106.8,
      accuracy: 8,
    });
    expect(roomService.broadcastToRoom).toHaveBeenCalledTimes(1);
    const [, payload] = (roomService.broadcastToRoom as any).mock.calls[0];
    const message = JSON.parse(payload);
    expect(message.type).toBe("room_alert");
    expect(message.payload.canResolve).toBeUndefined();
    expect(message.payload.status).toBeUndefined();
    expect(message.payload.source).toBeUndefined();
    expect(message.payload.magnitudeG).toBe(4.1);
    expect(message.payload.sosEventId).toBeUndefined();
    expect(message.payload.memberName).toBeUndefined();
    expect(message.payload.magnitude).toBeUndefined();
    expect(message.payload.x).toBeUndefined();
    expect(message.payload.y).toBeUndefined();
    expect(message.payload.z).toBeUndefined();
  });

  test("enforces a three-second server cooldown", async () => {
    let now = 1_000_000;
    const { controller, sosRepository } = makeController({ now: () => now });
    const { ws } = mockServerWebSocket({ userId: "owner-1", roomCode: "ABC123" });

    await controller.handleSosTriggerViaWs(ws, SHAKE, raw(now));
    now += 2_999;
    await controller.handleSosTriggerViaWs(ws, SHAKE, raw(now));
    now += 1;
    await controller.handleSosTriggerViaWs(ws, SHAKE, raw(now));

    expect(sosRepository.create).toHaveBeenCalledTimes(2);
  });

  test("does not rebroadcast an idempotent database replay", async () => {
    const { controller, roomService, sosRepository } = makeController();
    (sosRepository.create as any).mockImplementation(async (data: Record<string, unknown>) => ({
      created: false,
      event: { ...data, id: EVENT_ID, createdAt: new Date(), updatedAt: new Date() },
    }));
    const { ws } = mockServerWebSocket({ userId: "owner-1", roomCode: "ABC123" });

    await controller.handleSosTriggerViaWs(ws, SHAKE, raw());

    expect(sosRepository.create).toHaveBeenCalledTimes(1);
    expect(roomService.broadcastToRoom).not.toHaveBeenCalled();
  });
});
