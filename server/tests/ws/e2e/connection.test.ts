import { describe, expect, test, beforeAll, afterAll, beforeEach } from "bun:test";
import type { Server } from "bun";
import { TestContext } from "../../helpers/db-cleanup";
import { ApiRequest } from "../../helpers/api.request";
import app from "../../../src/app";
import { wsHandler, wsManager, type WsData } from "../../../src/features/ws/ws.module";
import { tokenUtils } from "../../../src/features/auth/auth.module";
import { deviceRepository } from "../../../src/features/device/device.repository";

const ctx = new TestContext();

let server: Server<WsData>;
let baseUrl: string;
let wsUrl: string;

const user = {
  name: "Naruto Uzumaki",
  email: "naruto.uzumaki@mail.com",
  password: "SayangHinat4!>.<",
};

beforeAll(() => {
  server = Bun.serve<WsData>({
    port: 0,
    async fetch(req, srv) {
      const url = new URL(req.url);
      if (url.pathname === "/ws") {
        const token = url.searchParams.get("token") || "";
        const deviceInstanceId = url.searchParams.get("deviceInstanceId") || "unknown";
        const manufacturer = url.searchParams.get("manufacturer") || "";
        const model = url.searchParams.get("model") || "";
        const androidVersion = url.searchParams.get("androidVersion") || "";

        const validated = token ? await tokenUtils.validateToken(token) : undefined;
        if (!validated) return new Response("Unauthorized", { status: 401 });

        await deviceRepository.upsert({
          userId: validated.id,
          deviceInstanceId,
          manufacturer,
          model,
          androidVersion,
        });

        const upgraded = srv.upgrade(req, {
          data: {
            userId: validated.id,
            deviceInstanceId,
            name: validated.name,
            email: validated.email,
            manufacturer,
            model,
            androidVersion,
            connectedAt: Date.now(),
          },
        });
        return upgraded ? undefined : new Response("WebSocket upgrade failed", { status: 400 });
      }
      return app.fetch(req);
    },
    websocket: wsHandler,
  });

  baseUrl = `http://127.0.0.1:${server.port}`;
  wsUrl = `ws://127.0.0.1:${server.port}/ws`;
});

afterAll(async () => {
  wsManager.clearConnections();
  server.stop(true);
});

beforeEach(() => ctx.cleanup());

function waitForMessage(
  ws: WebSocket,
  predicate: (msg: { type: string }) => boolean,
  timeoutMs = 2000
) {
  return new Promise<{ type: string; payload?: unknown }>((resolve, reject) => {
    const timer = setTimeout(() => {
      ws.removeEventListener("message", onMsg);
      reject(new Error("timeout waiting for message"));
    }, timeoutMs);

    function onMsg(ev: MessageEvent) {
      let parsed: { type: string; payload?: unknown };
      try {
        parsed = JSON.parse(ev.data as string);
      } catch {
        return;
      }
      if (predicate(parsed)) {
        clearTimeout(timer);
        ws.removeEventListener("message", onMsg);
        resolve(parsed);
      }
    }

    ws.addEventListener("message", onMsg);
  });
}

function openWs(url: string): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    const timer = setTimeout(() => reject(new Error("ws open timeout")), 2000);
    ws.addEventListener("open", () => {
      clearTimeout(timer);
      resolve(ws);
    });
    ws.addEventListener("error", (e) => {
      clearTimeout(timer);
      reject(e);
    });
  });
}

describe("WebSocket e2e: real Bun.serve upgrade path", () => {
  test("rejects connection without token (401)", async () => {
    await expect(openWs(`${wsUrl}?deviceInstanceId=dev-1`)).rejects.toBeDefined();
  });

  test("token → upgrade → ping → pong", async () => {
    const api = new ApiRequest();
    api.setHeader("X-Device-Id", "dev-1");
    api.setHeader("X-Device-Manufacturer", "TestCo");
    api.setHeader("X-Device-Model", "TestModel");
    const auth = await api.login(user);

    const url = `${wsUrl}?token=${encodeURIComponent(auth.token)}&deviceInstanceId=dev-1&manufacturer=TestCo&model=TestModel&androidVersion=14`;
    const ws = await openWs(url);

    try {
      ws.send(JSON.stringify({ type: "ping", timestamp: Date.now() }));
      const pong = await waitForMessage(ws, (m) => m.type === "pong");
      expect(pong.type).toBe("pong");
    } finally {
      ws.close();
    }
  });

  test("room_create → room_state with code", async () => {
    const api = new ApiRequest();
    api.setHeader("X-Device-Id", "dev-2");
    api.setHeader("X-Device-Manufacturer", "TestCo");
    api.setHeader("X-Device-Model", "TestModel");
    const auth = await api.login(user);

    const url = `${wsUrl}?token=${encodeURIComponent(auth.token)}&deviceInstanceId=dev-2&manufacturer=TestCo&model=TestModel&androidVersion=14`;
    const ws = await openWs(url);

    try {
      ws.send(JSON.stringify({ type: "room_create", timestamp: Date.now() }));
      const state = await waitForMessage(ws, (m) => m.type === "room_state");
      const payload = state.payload as { code: string; masterId: string };
      expect(payload.code).toMatch(/^[A-Z0-9]{6}$/);
      expect(payload.masterId).toBe(auth.user.id);
    } finally {
      ws.close();
    }
  });
});

describe("baseUrl exists", () => {
  test("server is listening", () => {
    expect(baseUrl).toMatch(/^http:\/\/127\.0\.0\.1:\d+$/);
  });
});
