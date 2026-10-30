import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test } from "bun:test";
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
const LOCATION_COUNT = 30;
const SOS_COUNT = 10;
const SOS_GAP_MS = 5_000;
const ctx = new TestContext();

let harness: EvidenceServer;
const sockets: EvidenceSocket[] = [];

type LatencyEvent = {
  kind: "location" | "sos";
  seq: number;
  tSendMs: number;
  tReceivedMs: number;
  deltaMs: number;
};

function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0;
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, index)] ?? 0;
}

function aggregate(kind: string, values: number[]): string[] {
  const sorted = [...values].sort((a, b) => a - b);
  const n = values.length;
  const mean = n === 0 ? 0 : values.reduce((sum, value) => sum + value, 0) / n;
  const median =
    n === 0
      ? 0
      : n % 2 === 1
        ? sorted[(n - 1) / 2] ?? 0
        : ((sorted[n / 2 - 1] ?? 0) + (sorted[n / 2] ?? 0)) / 2;
  return [
    `${kind}_aggregate`,
    "",
    "",
    "",
    "",
    String(n),
    mean.toFixed(3),
    median.toFixed(3),
    percentile(sorted, 95).toFixed(3),
    (sorted[0] ?? 0).toFixed(3),
    (sorted[n - 1] ?? 0).toFixed(3),
  ];
}

describe.skipIf(!ENABLED)("latency evidence", () => {
  beforeAll(() => {
    harness = startEvidenceServer();
  });

  afterAll(() => {
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

  test(
    "records location relay and sos_trigger latency for two authenticated clients",
    async () => {
      const suffix = uniqueSuffix();
      const hostAuth = await registerUser(
        "Host Latency",
        `host-lat-${suffix}@example.invalid`,
        PASSWORD,
        `host-lat-${suffix}`
      );
      const memberAuth = await registerUser(
        "Member Latency",
        `member-lat-${suffix}@example.invalid`,
        PASSWORD,
        `member-lat-${suffix}`
      );
      const host = await connectSocket(harness.wsUrl, hostAuth.token, `host-lat-${suffix}`);
      const member = await connectSocket(
        harness.wsUrl,
        memberAuth.token,
        `member-lat-${suffix}`
      );
      sockets.push(host, member);

      host.send("room_create", { shakeThreshold: 3.5, locationIntervalMs: 2000 });
      const created = await host.waitFor("room_state");
      const roomCode = String((created.payload as { code?: string } | undefined)?.code ?? "");
      expect(roomCode.length).toBe(6);
      member.send("room_join", { code: roomCode });
      await member.waitFor("room_state");
      await host.waitFor("user_joined");
      host.send("room_start");
      await member.waitFor("room_start");

      const events: LatencyEvent[] = [];

      for (let seq = 1; seq <= LOCATION_COUNT; seq++) {
        const tSend = member.send("location_update", {
          latitude: -6.2 + seq * 0.0001,
          longitude: 106.8 + seq * 0.0001,
          accuracy: 5,
        });
        const received = await host.waitFor("location_update", 2000);
        events.push({
          kind: "location",
          seq,
          tSendMs: tSend,
          tReceivedMs: received._tRecv,
          deltaMs: received._tRecv - tSend,
        });
      }

      for (let seq = 1; seq <= SOS_COUNT; seq++) {
        const tSend = member.send(
          "sos_trigger",
          { magnitudeG: 8, x: 0, y: 0, z: 80 },
          { idempotencyKey: crypto.randomUUID() }
        );
        const received = await host.waitFor("room_alert", 2000);
        events.push({
          kind: "sos",
          seq,
          tSendMs: tSend,
          tReceivedMs: received._tRecv,
          deltaMs: received._tRecv - tSend,
        });
        if (seq < SOS_COUNT) {
          await Bun.sleep(SOS_GAP_MS);
        }
      }

      const locationDeltas = events.filter((event) => event.kind === "location").map((e) => e.deltaMs);
      const sosDeltas = events.filter((event) => event.kind === "sos").map((e) => e.deltaMs);

      const csvRows = [
        ...events.map((event) => [
          event.kind,
          String(event.seq),
          event.tSendMs.toFixed(3),
          event.tReceivedMs.toFixed(3),
          event.deltaMs.toFixed(3),
          "",
          "",
          "",
          "",
          "",
          "",
        ]),
        aggregate("location", locationDeltas),
        aggregate("sos", sosDeltas),
      ];

      writeCsv(
        "latency.csv",
        [
          "kind",
          "seq",
          "t_send_ms",
          "t_received_ms",
          "delta_ms",
          "n",
          "mean_ms",
          "median_ms",
          "p95_ms",
          "min_ms",
          "max_ms",
        ],
        csvRows
      );

      expect(locationDeltas).toHaveLength(LOCATION_COUNT);
      expect(sosDeltas).toHaveLength(SOS_COUNT);
      for (const delta of [...locationDeltas, ...sosDeltas]) {
        expect(delta).toBeGreaterThanOrEqual(0);
      }
    },
    { timeout: 120_000 }
  );
});
