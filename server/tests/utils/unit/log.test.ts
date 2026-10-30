import { describe, expect, test } from "bun:test";
import { ts } from "../../../src/utils/log";

describe("ts", () => {
  test("returns current time in HH:mm:ss format", () => {
    const value = ts();

    expect(value).toMatch(/^\d{2}:\d{2}:\d{2}$/);
  });
});
