import { describe, expect, test } from "bun:test";
import app from "../../../src/app";

describe("strict thesis route scope", () => {
  const removedRoutes: Array<[string, string]> = [
    ["GET", "/"],
    ["GET", "/CHANGELOG.md"],
    ["GET", "/join/ABC123"],
    ["GET", "/.well-known/assetlinks.json"],
    ["GET", "/api/rooms"],
    ["PUT", "/api/auth/name"],
    ["POST", "/api/auth/refresh"],
    ["DELETE", "/api/users/me"],
  ];

  for (const [method, path] of removedRoutes) {
    test(`${method} ${path} is not exposed`, async () => {
      const response = await app.request(`http://localhost${path}`, { method });
      expect(response.status).toBe(404);
    });
  }
});
