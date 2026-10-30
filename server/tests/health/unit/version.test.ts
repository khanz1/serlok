import { afterEach, describe, expect, test } from "bun:test";
import { resolveLastChanges } from "../../../src/version";

describe("resolveLastChanges", () => {
  const previous = Bun.env.BUILD_LAST_CHANGES;

  afterEach(() => {
    if (previous === undefined) {
      delete Bun.env.BUILD_LAST_CHANGES;
      return;
    }

    Bun.env.BUILD_LAST_CHANGES = previous;
  });

  test("prefers BUILD_LAST_CHANGES when set", () => {
    Bun.env.BUILD_LAST_CHANGES = "forced last changes";

    expect(resolveLastChanges()).toBe("forced last changes");
  });
});
