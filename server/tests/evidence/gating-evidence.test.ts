import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test } from "bun:test";
import { count } from "drizzle-orm";
import { db } from "../../src/db";
import { roomMembers, sosEvents } from "../../src/db/schema";
import { sosRepository } from "../../src/features/sos/sos.repository";
import { TestContext } from "../helpers/db-cleanup";
import { writeCsv } from "./evidence-csv";
import {
  connectSocket,
  registerUser,
  startEvidenceServer,
  uniqueSuffix,
  type EvidenceServer,
  type EvidenceSocket,
} from "./ws-harness";

const ENABLED = Bun.env.SERLOK_EVIDENCE === "1";
const PASSWORD = "EvidencePass123";
const ctx = new TestContext();

type GatingRow = {
  aksi: string;
  expected: string;
  actual: string;
  http_ws_code: string;
  rows_affected: number;
  verdict: "pass" | "fail";
};

const rows: GatingRow[] = [];
const sockets: EvidenceSocket[] = [];

let harness: EvidenceServer;

function record(row: GatingRow): void {
  rows.push(row);
}

function flushCsv(): void {
  writeCsv(
    "gating_evidence.csv",
    ["aksi", "expected", "actual", "http_ws_code", "rows_affected", "verdict"],
    rows.map((row) => [
      row.aksi,
      row.expected,
      row.actual,
      row.http_ws_code,
      String(row.rows_affected),
      row.verdict,
    ])
  );
}

async function countTable(
  table: typeof sosEvents | typeof roomMembers
): Promise<number> {
  const [row] = await db.select({ n: count() }).from(table);
  return Number(row?.n ?? 0);
}

async function pair(): Promise<{
  hostAuth: Awaited<ReturnType<typeof registerUser>>;
  memberAuth: Awaited<ReturnType<typeof registerUser>>;
  host: EvidenceSocket;
  member: EvidenceSocket;
  roomCode: string;
}> {
  const suffix = uniqueSuffix();
  const hostAuth = await registerUser(
    "Host Evidence",
    `host-${suffix}@example.invalid`,
    PASSWORD,
    `host-${suffix}`
  );
  const memberAuth = await registerUser(
    "Member Evidence",
    `member-${suffix}@example.invalid`,
    PASSWORD,
    `member-${suffix}`
  );
  const host = await connectSocket(harness.wsUrl, hostAuth.token, `host-${suffix}`);
  const member = await connectSocket(harness.wsUrl, memberAuth.token, `member-${suffix}`);
  sockets.push(host, member);
  host.send("room_create", { shakeThreshold: 3.5, locationIntervalMs: 10000 });
  const created = await host.waitFor("room_state");
  const roomCode = String((created.payload as { code?: string } | undefined)?.code ?? "");
  member.send("room_join", { code: roomCode });
  await member.waitFor("room_state");
  await host.waitFor("user_joined");
  return { hostAuth, memberAuth, host, member, roomCode };
}

describe.skipIf(!ENABLED)("gating evidence", () => {
  beforeAll(() => {
    harness = startEvidenceServer();
  });

  afterAll(() => {
    flushCsv();
    harness?.stop();
  });

  beforeEach(async () => {
    await ctx.cleanup();
  });

  afterEach(async () => {
    for (const socket of sockets.splice(0)) {
      socket.close();
    }
    await Bun.sleep(50);
    await ctx.cleanup();
  });

  test("location_update is dropped when Safety Session is inactive", async () => {
    const { host, member } = await pair();
    const before = await countTable(sosEvents);
    member.send("location_update", {
      latitude: -6.2,
      longitude: 106.8,
      accuracy: 5,
    });
    const noRelay = await host.expectNo("location_update", 500);
    const after = await countTable(sosEvents);
    const delta = after - before;
    const verdict = noRelay && delta === 0 ? "pass" : "fail";
    record({
      aksi: "kirim location saat Safety Session tidak aktif",
      expected: "ditolak; 0 baris tersimpan; 0 frame ke anggota lain",
      actual: `silent_drop; sos_events_delta=${delta}; peer_location_frames=${noRelay ? 0 : 1}`,
      http_ws_code: "",
      rows_affected: delta,
      verdict,
    });
    expect(noRelay).toBe(true);
    expect(delta).toBe(0);
  });

  test("location_update is relayed when Safety Session is active", async () => {
    const { host, member, memberAuth } = await pair();
    host.send("room_start");
    await member.waitFor("room_start");
    const before = await countTable(sosEvents);
    member.send("location_update", {
      latitude: -6.2001,
      longitude: 106.8167,
      accuracy: 5,
    });
    const relayed = await host.waitFor("location_update");
    const after = await countTable(sosEvents);
    const delta = after - before;
    const payload = relayed.payload as { userId?: string; latitude?: number };
    const ok =
      payload.userId === memberAuth.user.id &&
      payload.latitude === -6.2001 &&
      delta === 0;
    record({
      aksi: "kirim location saat Safety Session aktif",
      expected: "diterima dan diteruskan ke anggota lain; 0 baris sos_events",
      actual: `relayed type=${relayed.type}; userId=${payload.userId ?? ""}; sos_events_delta=${delta}`,
      http_ws_code: "",
      rows_affected: delta,
      verdict: ok ? "pass" : "fail",
    });
    expect(relayed.type).toBe("location_update");
    expect(payload.userId).toBe(memberAuth.user.id);
    expect(payload.latitude).toBe(-6.2001);
    expect(delta).toBe(0);
  });

  test("WebSocket upgrade without JWT returns HTTP 401", async () => {
    const response = await fetch(`${harness.baseUrl}/ws`);
    const verdict = response.status === 401 ? "pass" : "fail";
    record({
      aksi: "WebSocket upgrade tanpa JWT",
      expected: "HTTP 401",
      actual: `HTTP ${response.status} ${await response.text()}`,
      http_ws_code: String(response.status),
      rows_affected: 0,
      verdict,
    });
    expect(response.status).toBe(401);
  });

  test("valid JWT that is not a Room member is dropped without HTTP 403", async () => {
    const { host } = await pair();
    const suffix = uniqueSuffix();
    const outsider = await registerUser(
      "Outsider",
      `out-${suffix}@example.invalid`,
      PASSWORD,
      `out-${suffix}`
    );
    const guest = await connectSocket(harness.wsUrl, outsider.token, `out-${suffix}`);
    sockets.push(guest);
    const before = await countTable(sosEvents);
    const membersBefore = await countTable(roomMembers);
    guest.send("location_update", { latitude: -6.2, longitude: 106.8, accuracy: 4 });
    guest.send("sos_trigger", { magnitudeG: 8, x: 0, y: 0, z: 80 });
    const noLocation = await host.expectNo("location_update", 500);
    const noAlert = await host.expectNo("room_alert", 500);
    const after = await countTable(sosEvents);
    const membersAfter = await countTable(roomMembers);
    const delta = after - before;
    const ok = noLocation && noAlert && delta === 0 && membersAfter === membersBefore;
    record({
      aksi: "JWT valid tetapi bukan anggota Room",
      expected: "ditolak; 0 baris sos_events; produksi tidak mengirim HTTP 403 pada upgrade WS",
      actual: `silent_drop; sos_events_delta=${delta}; room_members=${membersAfter}; peer_location=${noLocation ? 0 : 1}; peer_alert=${noAlert ? 0 : 1}`,
      http_ws_code: "",
      rows_affected: delta,
      verdict: ok ? "pass" : "fail",
    });
    expect(delta).toBe(0);
    expect(noLocation).toBe(true);
    expect(noAlert).toBe(true);
    expect(membersAfter).toBe(membersBefore);
  });

  test("sos_trigger is dropped when Safety Session is inactive", async () => {
    const { member } = await pair();
    const before = await countTable(sosEvents);
    member.send("sos_trigger", { magnitudeG: 8, x: 0, y: 0, z: 80 }, {
      idempotencyKey: crypto.randomUUID(),
    });
    await Bun.sleep(200);
    const after = await countTable(sosEvents);
    const delta = after - before;
    record({
      aksi: "sos_trigger saat sesi tidak aktif",
      expected: "ditolak; 0 baris sos_events",
      actual: `silent_drop; sos_events_delta=${delta}`,
      http_ws_code: "",
      rows_affected: delta,
      verdict: delta === 0 ? "pass" : "fail",
    });
    expect(delta).toBe(0);
  });

  test("valid sos_trigger inserts one sos_events row and recipient snapshot", async () => {
    const { host, member, roomCode } = await pair();
    host.send("room_start");
    await member.waitFor("room_start");
    member.send("location_update", {
      latitude: -6.2,
      longitude: 106.8,
      accuracy: 5,
    });
    await host.waitFor("location_update");
    const before = await countTable(sosEvents);
    member.send(
      "sos_trigger",
      { magnitudeG: 8, x: 1, y: 2, z: 3 },
      { idempotencyKey: crypto.randomUUID() }
    );
    const alert = await host.waitFor("room_alert");
    const after = await countTable(sosEvents);
    const delta = after - before;
    const snapshot = await sosRepository.listPublicByRoom(roomCode);
    const ok = delta === 1 && alert.type === "room_alert" && snapshot.length === 1;
    record({
      aksi: "sos_trigger valid",
      expected: "1 baris sos_events plus snapshot penerima room_alert",
      actual: `sos_events_delta=${delta}; room_alert=${alert.type}; snapshot_incidents=${snapshot.length}`,
      http_ws_code: "",
      rows_affected: delta,
      verdict: ok ? "pass" : "fail",
    });
    expect(delta).toBe(1);
    expect(alert.type).toBe("room_alert");
    expect(snapshot.length).toBe(1);
  });

  test("second sos_trigger within 3 seconds is blocked by server cooldown", async () => {
    const { host, member } = await pair();
    host.send("room_start");
    await member.waitFor("room_start");
    member.send("sos_trigger", { magnitudeG: 8, x: 0, y: 0, z: 80 }, {
      idempotencyKey: crypto.randomUUID(),
    });
    await host.waitFor("room_alert");
    const beforeSecond = await countTable(sosEvents);
    member.send("sos_trigger", { magnitudeG: 8, x: 0, y: 0, z: 81 }, {
      idempotencyKey: crypto.randomUUID(),
    });
    const noSecondAlert = await host.expectNo("room_alert", 500);
    const afterSecond = await countTable(sosEvents);
    const delta = afterSecond - beforeSecond;
    const ok = delta === 0 && noSecondAlert;
    record({
      aksi: "sos_trigger kedua dalam kurang dari 3 detik",
      expected: "ditolak cooldown server; 0 baris baru",
      actual: `silent_drop; sos_events_delta=${delta}; extra_room_alert=${noSecondAlert ? 0 : 1}`,
      http_ws_code: "",
      rows_affected: delta,
      verdict: ok ? "pass" : "fail",
    });
    expect(delta).toBe(0);
    expect(noSecondAlert).toBe(true);
  });

  test("sos_resolve is rejected as an out-of-contract type", async () => {
    const { host, member } = await pair();
    host.send("room_start");
    await member.waitFor("room_start");
    member.send("sos_trigger", { magnitudeG: 8, x: 0, y: 0, z: 80 }, {
      idempotencyKey: crypto.randomUUID(),
    });
    const alert = await host.waitFor("room_alert");
    const incidentId = String((alert.payload as { incidentId?: string } | undefined)?.incidentId ?? "");
    const before = await countTable(sosEvents);
    host.send("sos_resolve", { incidentId });
    await Bun.sleep(200);
    const after = await countTable(sosEvents);
    const delta = after - before;
    record({
      aksi: "sos_resolve oleh non-pemilik event",
      expected: "ditolak; 0 baris baru; tipe di luar kontrak bukan ACL owner",
      actual: `zod_reject_or_unhandled; sos_events_delta=${delta}`,
      http_ws_code: "",
      rows_affected: delta,
      verdict: delta === 0 ? "pass" : "fail",
    });
    expect(delta).toBe(0);
  });

  test("route outside the contract returns HTTP 404", async () => {
    const response = await fetch(`${harness.baseUrl}/api/rooms`);
    record({
      aksi: "route di luar kontrak GET /api/rooms",
      expected: "HTTP 404",
      actual: `HTTP ${response.status}`,
      http_ws_code: String(response.status),
      rows_affected: 0,
      verdict: response.status === 404 ? "pass" : "fail",
    });
    expect(response.status).toBe(404);
  });
});
