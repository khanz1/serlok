import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { UserSeeder } from "../../../scripts/seeders/user.seed";
import { sosRepository } from "../../../src/features/sos/sos.repository";
import { TestContext } from "../../helpers/db-cleanup";

const ctx = new TestContext();
const users = new UserSeeder();

describe("SosRepository", () => {
  beforeEach(() => ctx.cleanup());
  afterEach(() => ctx.cleanup());

  test("returns the original incident for an idempotent replay", async () => {
    const owner = await users.single({
      name: "Owner",
      email: "owner@example.com",
      password: "PassPass123",
    });
    const input = {
      roomCode: "ABC123",
      userId: owner.id,
      idempotencyKey: "22222222-2222-4222-8222-222222222222",
      magnitude: 4.1,
      x: 40,
      y: 0,
      z: 0,
    };

    const first = await sosRepository.create(input);
    const replay = await sosRepository.create(input);

    expect(first.created).toBe(true);
    expect(replay.created).toBe(false);
    expect(replay.event.id).toBe(first.event.id);
  });

  test("lists public Room incidents newest first without raw axes", async () => {
    const owner = await users.single({
      name: "Kartika Widyasari",
      email: "kartika@example.com",
      password: "PassPass123",
    });
    const other = await users.single({
      name: "Other Room",
      email: "other@example.com",
      password: "PassPass123",
    });

    await sosRepository.create({
      roomCode: "SRLK46",
      userId: owner.id,
      idempotencyKey: "11111111-1111-4111-8111-111111111111",
      magnitude: 3.9,
      x: 10,
      y: 20,
      z: 30,
      latitude: -6.21,
      longitude: 106.81,
      accuracy: 6,
    });
    await sosRepository.create({
      roomCode: "SRLK46",
      userId: owner.id,
      idempotencyKey: "22222222-2222-4222-8222-222222222222",
      magnitude: 4.3,
      x: 1,
      y: 2,
      z: 3,
      latitude: -6.2,
      longitude: 106.8,
      accuracy: 8,
    });
    await sosRepository.create({
      roomCode: "OTHER1",
      userId: other.id,
      idempotencyKey: "33333333-3333-4333-8333-333333333333",
      magnitude: 9.9,
      x: 0,
      y: 0,
      z: 0,
    });

    const incidents = await sosRepository.listPublicByRoom("SRLK46");

    expect(incidents).toHaveLength(2);
    expect(incidents[0]?.magnitudeG).toBe(4.3);
    expect(incidents[0]?.name).toBe("Kartika Widyasari");
    expect(incidents[0]?.location).toEqual({
      latitude: -6.2,
      longitude: 106.8,
      accuracy: 8,
    });
    expect(incidents[1]?.magnitudeG).toBe(3.9);
    expect(incidents.map((item) => item.incidentId)).toEqual(
      expect.arrayContaining([expect.any(String)])
    );
    expect(JSON.stringify(incidents)).not.toContain('"x"');
    expect(JSON.stringify(incidents)).not.toContain('"y"');
    expect(JSON.stringify(incidents)).not.toContain('"z"');
    expect(incidents.some((item) => item.name === "Other Room")).toBe(false);
  });

  test("keeps a list row when location is missing and caps at the given limit", async () => {
    const owner = await users.single({
      name: "Angga Maulana",
      email: "angga@example.com",
      password: "PassPass123",
    });
    for (let index = 0; index < 3; index += 1) {
      await sosRepository.create({
        roomCode: "EMPTY1",
        userId: owner.id,
        idempotencyKey: `44444444-4444-4444-8444-44444444444${index}`,
        magnitude: 3 + index,
        x: 0,
        y: 0,
        z: 0,
      });
    }

    const incidents = await sosRepository.listPublicByRoom("EMPTY1", 2);

    expect(incidents).toHaveLength(2);
    expect(incidents[0]?.location).toBeNull();
    expect(incidents[1]?.location).toBeNull();
  });
});
