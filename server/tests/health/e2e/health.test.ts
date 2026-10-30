import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, test } from "bun:test";
import { ApiRequest } from "../../helpers/api.request";

const api = new ApiRequest();
const apiUrl = "/api/health";
const packageJson = JSON.parse(
  readFileSync(join(import.meta.dir, "../../../package.json"), "utf8")
) as { name: string; version: string };

type HealthResponse = {
  status: string;
  uptime: number;
  timestamp: string;
  name: string;
  version: string;
  lastChanges: string;
};

function gitLastChanges(): string {
  const result = Bun.spawnSync({
    cmd: ["git", "log", "-1", "--pretty=format:%h %s (%cs)"],
    cwd: join(import.meta.dir, "../../../.."),
  });

  return result.stdout.toString().trim();
}

describe("E2E: GET /api/health", () => {
  test("returns health status", async () => {
    const res = await api.get(apiUrl);

    expect(res.status).toBe(200);
    const body = (await res.json()) as HealthResponse;

    expect(body.status).toBe("ok");
    expect(typeof body.uptime).toBe("number");
    expect(body.uptime).toBeGreaterThanOrEqual(0);
    expect(Date.parse(body.timestamp)).not.toBeNaN();
    expect(body).not.toHaveProperty("connections");
  });

  test("returns app name and version from package.json", async () => {
    const res = await api.get(apiUrl);

    expect(res.status).toBe(200);
    const body = (await res.json()) as HealthResponse;

    expect(body.name).toBe(packageJson.name);
    expect(body.version).toBe(packageJson.version);
  });

  test("returns last changes from the repository", async () => {
    const res = await api.get(apiUrl);

    expect(res.status).toBe(200);
    const body = (await res.json()) as HealthResponse;

    expect(body.lastChanges).toBe(gitLastChanges());
  });

  test("readiness verifies the database without exposing internals", async () => {
    const res = await api.get("/api/ready");
    expect(res.status).toBe(200);
    const body = (await res.json()) as { status: string; timestamp: string };
    expect(body.status).toBe("ready");
    expect(Date.parse(body.timestamp)).not.toBeNaN();
    expect(body).not.toHaveProperty("connections");
  });
});
