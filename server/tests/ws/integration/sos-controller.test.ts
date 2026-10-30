import { describe, expect, test, beforeEach, afterEach } from "bun:test";
import { TestContext } from "../../helpers/db-cleanup";
import { UserSeeder } from "../../../scripts/seeders/user.seed";
import { RoomService } from "../../../src/features/room/room.service";
import { roomRepository } from "../../../src/features/room/room.repository";
import { SosController } from "../../../src/features/sos/sos.controller";
import { sosRepository } from "../../../src/features/sos/sos.repository";
import { AlertCooldown } from "../../../src/features/ws/throttle";
import { mockServerWebSocket } from "../../helpers/mock-websocket";
import { db } from "../../../src/db";
import { sosEvents } from "../../../src/db/schema";
import { eq } from "drizzle-orm";

const ctx = new TestContext();
const userSeeder = new UserSeeder();
const eventsForRoom = (code: string) =>
  db.select().from(sosEvents).where(eq(sosEvents.roomCode, code));

describe("SosController alert path (integration with real DB)", () => {
  beforeEach(() => ctx.cleanup());
  afterEach(() => ctx.cleanup());

  test("accepted shake trigger inserts a SOS event row with location", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const roomService = new RoomService(roomRepository);
    const controller = new SosController({
      roomService,
      sosRepository,
      cooldown: new AlertCooldown(),
      now: () => 1_000_000,
    });

    const handle = mockServerWebSocket({
      userId: u1.id,
      deviceInstanceId: "dev-1",
    });
    const { code } = await roomService.createRoom(handle.ws);
    await roomService.setSharing(code, true);
    roomService.updateLastLocation(code, u1.id, 12.34, 56.78);

    await controller.handleSosTriggerViaWs(
      handle.ws,
      { magnitudeG: 7.5, x: 1, y: 2, z: 3 },
      JSON.stringify({ type: "sos_trigger", timestamp: 1_000_000 })
    );

    const events = await eventsForRoom(code);
    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({
      roomCode: code,
      userId: u1.id,
      latitude: 12.34,
      longitude: 56.78,
    });
    expect(events[0]!.magnitude).toBe(7.5);
  });

  test("shake event is ignored while room sharing is off", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const roomService = new RoomService(roomRepository);
    const controller = new SosController({
      roomService,
      sosRepository,
      cooldown: new AlertCooldown(),
    });

    const handle = mockServerWebSocket({ userId: u1.id });
    const { code } = await roomService.createRoom(handle.ws);

    await controller.handleSosTriggerViaWs(handle.ws, { magnitudeG: 9, x: 0, y: 0, z: 0 }, "{}");

    const events = await eventsForRoom(code);
    expect(events).toHaveLength(0);
  });

  test("shake event below room threshold is ignored", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const roomService = new RoomService(roomRepository);
    const controller = new SosController({
      roomService,
      sosRepository,
      cooldown: new AlertCooldown(),
    });

    const handle = mockServerWebSocket({ userId: u1.id });
    const { code } = await roomService.createRoom(handle.ws);
    await roomService.setSharing(code, true);
    await roomService.setShakeThreshold(code, 4.5);

    await controller.handleSosTriggerViaWs(handle.ws, { magnitudeG: 4, x: 0, y: 0, z: 0 }, "{}");

    const events = await eventsForRoom(code);
    expect(events).toHaveLength(0);
  });

  test("cooldown blocks duplicate inserts within window", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const roomService = new RoomService(roomRepository);
    let nowMs = 1_000_000_000_000;
    const controller = new SosController({
      roomService,
      sosRepository,
      cooldown: new AlertCooldown(3_000),
      now: () => nowMs,
    });

    const handle = mockServerWebSocket({ userId: u1.id });
    const { code } = await roomService.createRoom(handle.ws);
    await roomService.setSharing(code, true);

    await controller.handleSosTriggerViaWs(
      handle.ws,
      { magnitudeG: 8, x: 0, y: 0, z: 0 },
      JSON.stringify({ timestamp: nowMs })
    );
    nowMs += 2_999;
    await controller.handleSosTriggerViaWs(
      handle.ws,
      { magnitudeG: 8, x: 0, y: 0, z: 0 },
      JSON.stringify({ timestamp: nowMs })
    );
    nowMs += 1;
    await controller.handleSosTriggerViaWs(
      handle.ws,
      { magnitudeG: 8, x: 0, y: 0, z: 0 },
      JSON.stringify({ timestamp: nowMs })
    );

    const events = await eventsForRoom(code);
    expect(events).toHaveLength(2);
  });

  test("SOS after detach and rebind persists without a client room_join", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const roomService = new RoomService(roomRepository);
    const controller = new SosController({
      roomService,
      sosRepository,
      cooldown: new AlertCooldown(),
      now: () => 1_000_000,
    });

    const first = mockServerWebSocket({ userId: u1.id });
    const { code } = await roomService.createRoom(first.ws);
    await roomService.setSharing(code, true);
    roomService.updateLastLocation(code, u1.id, 12.34, 56.78);
    await roomService.detachSocket(first.ws);

    const rebound = mockServerWebSocket({ userId: u1.id });
    await roomService.rebindMembership(rebound.ws);

    await controller.handleSosTriggerViaWs(
      rebound.ws,
      { magnitudeG: 7.5, x: 1, y: 2, z: 3 },
      JSON.stringify({ type: "sos_trigger", timestamp: 1_000_000 })
    );

    const events = await eventsForRoom(code);
    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({
      roomCode: code,
      userId: u1.id,
      magnitude: 7.5,
    });
    roomService.dispose();
  });

  test("SOS on a new socket is dropped until membership is rebound", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const roomService = new RoomService(roomRepository);
    const controller = new SosController({
      roomService,
      sosRepository,
      cooldown: new AlertCooldown(),
      now: () => 1_000_000,
    });

    const first = mockServerWebSocket({ userId: u1.id });
    const { code } = await roomService.createRoom(first.ws);
    await roomService.setSharing(code, true);
    await roomService.detachSocket(first.ws);

    const unbound = mockServerWebSocket({ userId: u1.id });
    await controller.handleSosTriggerViaWs(
      unbound.ws,
      { magnitudeG: 7.5, x: 1, y: 2, z: 3 },
      JSON.stringify({ type: "sos_trigger", timestamp: 1_000_000 })
    );

    expect(await eventsForRoom(code)).toHaveLength(0);
    roomService.dispose();
  });
});
