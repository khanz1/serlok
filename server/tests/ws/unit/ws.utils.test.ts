import { describe, expect, test } from "bun:test";
import { elapsed } from "../../../src/features/ws/ws.utils";

describe("elapsed", () => {
  test("returns seconds for sub-minute durations", () => {
    expect(elapsed(0)).toBe("0s");
    expect(elapsed(500)).toBe("1s");
    expect(elapsed(1_000)).toBe("1s");
    expect(elapsed(59_999)).toBe("60s");
  });

  test("returns minutes for sub-hour durations", () => {
    expect(elapsed(60_000)).toBe("1.0m");
    expect(elapsed(150_000)).toBe("2.5m");
    expect(elapsed(3_599_999)).toBe("60.0m");
  });

  test("returns hours for hour-or-greater durations", () => {
    expect(elapsed(3_600_000)).toBe("1.0h");
    expect(elapsed(5_400_000)).toBe("1.5h");
    expect(elapsed(36_000_000)).toBe("10.0h");
  });
});
