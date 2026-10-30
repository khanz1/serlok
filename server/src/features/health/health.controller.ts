import { Hono } from "hono";
import { client } from "../../db";
import { APP_NAME, APP_VERSION, LAST_CHANGES } from "../../version";

const health = new Hono();
const startedAt = Bun.nanoseconds();

function livePayload() {
  return {
    status: "ok",
    name: APP_NAME,
    version: APP_VERSION,
    lastChanges: LAST_CHANGES,
    uptime: (Bun.nanoseconds() - startedAt) / 1_000_000_000,
    timestamp: new Date().toISOString(),
  };
}

export async function assertDatabaseReady(): Promise<void> {
  await client`select 1`;
}

health.get("/", (c) => c.json(livePayload()));

export default health;
