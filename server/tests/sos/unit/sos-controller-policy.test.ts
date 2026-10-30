import { describe, expect, mock, test } from "bun:test";
import { SosController } from "../../../src/features/sos/sos.controller";
import { AlertCooldown } from "../../../src/features/ws/throttle";
import { mockServerWebSocket } from "../../helpers/mock-websocket";

const EVENT_ID = "11111111-1111-4111-8111-111111111111";
const IDEMPOTENCY_ID = "22222222-2222-4222-8222-222222222222";

function makeController(options: { isSharing?: boolean; now?: () => number } = {}) {
  const roomService = {
    getRoom: mock(async (code: string) => ({
      code,
      masterId: "owner-1",
      isSharing: options.isSharing ?? true,
      shakeThreshold: 3.5,
    })),
    broadcastToRoom: mock((_code: string, _payload: string) => {}),
    getLastLocation: mock(() => ({ latitude: -6.2, longitude: 106.8, accuracy: 8 })),
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

function triggerRaw(timestamp = 1_000_000) {
  return JSON.stringify({ type: "sos_trigger", timestamp, idempotencyKey: IDEMPOTENCY_ID });
}

describe("SOS authorization and lifecycle", () => {
  test("rejects SOS while the Safety Session is inactive", async () => {
    const { controller, sosRepository } = makeController({ isSharing: false });
    const sender = mockServerWebSocket({ userId: "owner-1", roomCode: "ABC123" });

    await controller.handleSosTriggerViaWs(
      sender.ws,
      { magnitudeG: 4.1, x: 40, y: 0, z: 0 },
      triggerRaw()
    );

    expect(sosRepository.create).not.toHaveBeenCalled();
  });

  test("accepts one valid shake crossing and stores G-Force with location", async () => {
    const { controller, sosRepository } = makeController();
    const sender = mockServerWebSocket({ userId: "member-2", roomCode: "ABC123" });

    await controller.handleSosTriggerViaWs(
      sender.ws,
      {
        magnitudeG: 4.1,
        x: 40,
        y: 0,
        z: 0,
      },
      triggerRaw()
    );

    expect((sosRepository.create as any).mock.calls[0][0]).toMatchObject({
      userId: "member-2",
      magnitude: 4.1,
      latitude: -6.2,
      longitude: 106.8,
    });
  });

  test("applies the three-second server cooldown", async () => {
    let now = 1_000_000;
    const { controller, sosRepository } = makeController({ now: () => now });
    const sender = mockServerWebSocket({ userId: "owner-1", roomCode: "ABC123" });

    const shake = { magnitudeG: 4.1, x: 40, y: 0, z: 0 };
    await controller.handleSosTriggerViaWs(sender.ws, shake, triggerRaw(now));
    now += 2_999;
    await controller.handleSosTriggerViaWs(sender.ws, shake, triggerRaw(now));
    now += 1;
    await controller.handleSosTriggerViaWs(sender.ws, shake, triggerRaw(now));

    expect(sosRepository.create).toHaveBeenCalledTimes(2);
  });

  test("broadcasts one room_alert without resolution fields", async () => {
    const { controller, roomService } = makeController();
    const sender = mockServerWebSocket({
      userId: "owner-1",
      name: "Alice",
      roomCode: "ABC123",
    });

    await controller.handleSosTriggerViaWs(
      sender.ws,
      { magnitudeG: 4.1, x: 40, y: 0, z: 0 },
      triggerRaw()
    );

    expect(roomService.broadcastToRoom).toHaveBeenCalledTimes(1);
    const [, payload] = (roomService.broadcastToRoom as any).mock.calls[0];
    const message = JSON.parse(payload);
    expect(message.type).toBe("room_alert");
    expect(message.payload).toMatchObject({
      incidentId: EVENT_ID,
      userId: "owner-1",
      name: "Alice",
      magnitudeG: 4.1,
    });
    expect(message.payload.canResolve).toBeUndefined();
    expect(message.payload.status).toBeUndefined();
  });
});
