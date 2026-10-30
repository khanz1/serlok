import { describe, expect, test } from "bun:test";
import { AlertCooldown, LocationThrottle } from "../../../src/features/ws/throttle";

describe("LocationThrottle", () => {
  test("returns null until interval elapses", () => {
    const t = new LocationThrottle(5_000);
    expect(t.record("u1", 0)).toBeNull();
    expect(t.record("u1", 4_999)).toBeNull();
  });

  test("emits snapshot at interval boundary then resets count", () => {
    const t = new LocationThrottle(5_000);
    t.record("u1", 0);
    t.record("u1", 1_000);
    const snap = t.record("u1", 5_000);
    expect(snap).toEqual({ count: 3, fps: 0.6 });
    expect(t.record("u1", 6_000)).toBeNull();
  });

  test("isolates counters per user", () => {
    const t = new LocationThrottle(5_000);
    t.record("u1", 0);
    t.record("u2", 0);
    const a = t.record("u1", 5_000);
    const b = t.record("u2", 5_000);
    expect(a?.count).toBe(2);
    expect(b?.count).toBe(2);
  });

  test("delete clears single user", () => {
    const t = new LocationThrottle(5_000);
    t.record("u1", 0);
    t.delete("u1");
    expect(t.record("u1", 0)).toBeNull();
  });

  test("reset clears all users", () => {
    const t = new LocationThrottle(5_000);
    t.record("u1", 0);
    t.record("u2", 0);
    t.reset();
    expect(t.record("u1", 0)).toBeNull();
    expect(t.record("u2", 0)).toBeNull();
  });
});

describe("AlertCooldown", () => {
  test("first call always fires", () => {
    const c = new AlertCooldown();
    expect(c.shouldFire("u1", 0)).toBe(true);
  });

  test("blocks second call within the three-second interval", () => {
    const c = new AlertCooldown();
    c.shouldFire("u1", 0);
    expect(c.shouldFire("u1", 2_999)).toBe(false);
  });

  test("allows at the three-second boundary", () => {
    const c = new AlertCooldown();
    c.shouldFire("u1", 0);
    expect(c.shouldFire("u1", 3_000)).toBe(true);
  });

  test("isolates per user", () => {
    const c = new AlertCooldown();
    c.shouldFire("u1", 0);
    expect(c.shouldFire("u2", 0)).toBe(true);
  });

  test("delete + reset", () => {
    const c = new AlertCooldown();
    c.shouldFire("u1", 0);
    c.delete("u1");
    expect(c.shouldFire("u1", 0)).toBe(true);
    c.shouldFire("u2", 0);
    c.reset();
    expect(c.shouldFire("u2", 0)).toBe(true);
  });
});
