import { describe, expect, test, beforeEach, afterEach } from "bun:test";
import { TestContext } from "../../helpers/db-cleanup";
import { UserSeeder } from "../../../scripts/seeders/user.seed";
import { RoomService } from "../../../src/features/room/room.service";
import { roomRepository } from "../../../src/features/room/room.repository";
import { mockServerWebSocket } from "../../helpers/mock-websocket";

const ctx = new TestContext();
const userSeeder = new UserSeeder();

describe("RoomService (integration with real DB)", () => {
  beforeEach(() => ctx.cleanup());
  afterEach(() => ctx.cleanup());

  test("createRoom persists room and registers socket", async () => {
    const user = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const handle = mockServerWebSocket({ userId: user.id });

    const result = await service.createRoom(handle.ws);

    expect(result.code).toMatch(/^[A-Z0-9]{6}$/);
    expect(result.masterId).toBe(user.id);
    expect(result.isSharing).toBe(false);
    expect(result.shakeThreshold).toBe(3.5);
    expect(handle.ws.data.roomCode).toBe(result.code);

    const fromDb = await service.getRoom(result.code);
    expect(fromDb?.masterId).toBe(user.id);
    expect(service.getSockets(result.code)?.size).toBe(1);
  });

  test("joinRoom adds member and broadcastToRoom reaches both clients", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const u2 = await userSeeder.single({
      name: "Bob",
      email: "bob@b.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const a = mockServerWebSocket({ userId: u1.id });
    const b = mockServerWebSocket({ userId: u2.id });

    const { code } = await service.createRoom(a.ws);
    expect(await service.joinRoom(code, b.ws)).toBe("ok");

    service.broadcastToRoom(code, "hello");
    expect(a.sent).toEqual(["hello"]);
    expect(b.sent).toEqual(["hello"]);

    a.reset();
    b.reset();
    service.broadcastToRoom(code, "private", u2.id);
    expect(a.sent).toEqual(["private"]);
    expect(b.sent).toEqual([]);
  });

  test("joinRoom on missing code returns not_found", async () => {
    const service = new RoomService(roomRepository);
    const { ws } = mockServerWebSocket({ userId: "00000000-0000-0000-0000-000000000000" });
    expect(await service.joinRoom("ZZZZZZ", ws)).toBe("not_found");
  });

  test("host leaving soft-closes the Room instead of handing off", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const u2 = await userSeeder.single({
      name: "Bob",
      email: "bob@b.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const a = mockServerWebSocket({ userId: u1.id });
    const b = mockServerWebSocket({ userId: u2.id });

    const { code } = await service.createRoom(a.ws);
    await service.joinRoom(code, b.ws);
    const result = await service.leaveRoom(a.ws);

    expect(result?.softClosed).toBe(true);
    expect(result && result.softClosed ? result.remaining : []).toEqual([b.ws]);
    expect(b.ws.data.roomCode).toBeUndefined();
    const fromDb = await service.getRoom(code);
    expect(fromDb?.closedAt).not.toBeNull();
    expect(service.getSockets(code)).toBeUndefined();
    expect(await service.joinRoom(code, b.ws)).toBe("closed");
  });

  test("last member leaving soft-closes the room", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const handle = mockServerWebSocket({ userId: u1.id });

    const { code } = await service.createRoom(handle.ws);
    const leave = await service.leaveRoom(handle.ws);
    expect(leave?.softClosed).toBe(true);
    const fromDb = await service.getRoom(code);
    expect(fromDb).toBeDefined();
    expect(fromDb?.closedAt).not.toBeNull();
    expect(await service.joinRoom(code, handle.ws)).toBe("closed");
    expect(service.getSockets(code)).toBeUndefined();
  });

  test("setSharing persists isSharing flag", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const handle = mockServerWebSocket({ userId: u1.id });

    const { code } = await service.createRoom(handle.ws);
    await service.setSharing(code, true);
    expect((await service.getRoom(code))?.isSharing).toBe(true);
    await service.setSharing(code, false);
    expect((await service.getRoom(code))?.isSharing).toBe(false);
  });

  test("setShakeThreshold persists the room threshold", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const handle = mockServerWebSocket({ userId: u1.id });

    const { code } = await service.createRoom(handle.ws);
    await service.setShakeThreshold(code, 4.5);

    expect((await service.getRoom(code))?.shakeThreshold).toBe(4.5);
  });

  test("updateLastLocation + getLastLocation round-trip", () => {
    const service = new RoomService(roomRepository);
    expect(service.getLastLocation("ABC123", "user-x")).toBeUndefined();
    service.updateLastLocation("ABC123", "user-x", 12.34, 56.78);
    expect(service.getLastLocation("ABC123", "user-x")).toEqual({
      latitude: 12.34,
      longitude: 56.78,
    });
    expect(service.getLastLocation("OTHER1", "user-x")).toBeUndefined();
  });

  test("host detach keeps the Room open and Safety Session active", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const u2 = await userSeeder.single({
      name: "Bob",
      email: "bob@b.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const a = mockServerWebSocket({ userId: u1.id });
    const b = mockServerWebSocket({ userId: u2.id });

    const { code } = await service.createRoom(a.ws);
    await service.joinRoom(code, b.ws);
    await service.setSharing(code, true);
    service.updateLastLocation(code, u1.id, 12.34, 56.78);

    const detached = await service.detachSocket(a.ws);

    expect(detached?.code).toBe(code);
    expect(a.ws.data.roomCode).toBeUndefined();
    expect(service.getSockets(code)?.has(u1.id)).toBe(false);
    expect(service.getSockets(code)?.get(u2.id)).toBe(b.ws);
    const fromDb = await service.getRoom(code);
    expect(fromDb?.closedAt).toBeNull();
    expect(fromDb?.isSharing).toBe(true);
    expect(await service.getMemberIds(code)).toEqual(expect.arrayContaining([u1.id, u2.id]));
    expect(service.getLastLocation(code, u1.id)).toEqual({
      latitude: 12.34,
      longitude: 56.78,
    });
    service.dispose();
  });

  test("rebindMembership restores roomCode after detach", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const first = mockServerWebSocket({ userId: u1.id });
    const { code } = await service.createRoom(first.ws);
    await service.setSharing(code, true);
    await service.detachSocket(first.ws);

    const second = mockServerWebSocket({ userId: u1.id });
    const rebound = await service.rebindMembership(second.ws);

    expect(rebound?.code).toBe(code);
    expect(second.ws.data.roomCode).toBe(code);
    expect(service.getSockets(code)?.get(u1.id)).toBe(second.ws);
    expect((await service.getRoom(code))?.closedAt).toBeNull();
    service.dispose();
  });

  test("member leave while Host is detached does not close the Room", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const u2 = await userSeeder.single({
      name: "Bob",
      email: "bob@b.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const host = mockServerWebSocket({ userId: u1.id });
    const member = mockServerWebSocket({ userId: u2.id });

    const { code } = await service.createRoom(host.ws);
    await service.joinRoom(code, member.ws);
    await service.setSharing(code, true);
    await service.detachSocket(host.ws);

    const leave = await service.leaveRoom(member.ws);

    expect(leave?.softClosed).toBeFalsy();
    const fromDb = await service.getRoom(code);
    expect(fromDb?.closedAt).toBeNull();
    expect(fromDb?.isSharing).toBe(true);
    expect(await service.getMemberIds(code)).toEqual([u1.id]);
    service.dispose();
  });

  test("empty sockets past the injected delay soft-closes the Room", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository, { emptySocketCloseMs: 30 });
    const handle = mockServerWebSocket({ userId: u1.id });
    const { code } = await service.createRoom(handle.ws);
    await service.setSharing(code, true);

    await service.detachSocket(handle.ws);
    expect((await service.getRoom(code))?.closedAt).toBeNull();

    await Bun.sleep(60);

    const fromDb = await service.getRoom(code);
    expect(fromDb?.closedAt).not.toBeNull();
    expect(fromDb?.isSharing).toBe(false);
    service.dispose();
  });

  test("rebind before the empty-socket delay keeps the Room open", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository, { emptySocketCloseMs: 40 });
    const first = mockServerWebSocket({ userId: u1.id });
    const { code } = await service.createRoom(first.ws);
    await service.detachSocket(first.ws);

    const second = mockServerWebSocket({ userId: u1.id });
    await service.rebindMembership(second.ws);
    await Bun.sleep(70);

    expect((await service.getRoom(code))?.closedAt).toBeNull();
    service.dispose();
  });

  test("roomStatePayload includes member metadata from sockets", async () => {
    const u1 = await userSeeder.single({
      name: "Alice",
      email: "alice@a.com",
      password: "PassPass123",
    });
    const service = new RoomService(roomRepository);
    const handle = mockServerWebSocket({
      userId: u1.id,
      name: "Alice",
      email: "alice@a.com",
      manufacturer: "Pixel",
      model: "8 Pro",
      androidVersion: "14",
      deviceInstanceId: "dev-alice",
    });
    const { code } = await service.createRoom(handle.ws);
    const payload = service.roomStatePayload(code, u1.id, false);
    expect(payload).toEqual({
      code,
      masterId: u1.id,
      isSharing: false,
      shakeThreshold: 3.5,
      locationIntervalMs: 10_000,
      members: [
        {
          userId: u1.id,
          deviceInstanceId: "dev-alice",
          name: "Alice",
          email: "alice@a.com",
          manufacturer: "Pixel",
          model: "8 Pro",
          androidVersion: "14",
        },
      ],
    });
  });
});
