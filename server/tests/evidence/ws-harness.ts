import type { Server } from "bun";
import app from "../../src/app";
import { wsHandler, wsManager, type WsData } from "../../src/features/ws/ws.module";
import { tokenUtils } from "../../src/features/auth/auth.module";
import { deviceRepository } from "../../src/features/device/device.repository";
import { ApiRequest } from "../helpers/api.request";
import type { ApiSuccessResponse } from "../../src/utils/api-response";
import type { AuthResult } from "../../src/features/auth/auth.type";

export interface EvidenceServer {
  server: Server<WsData>;
  baseUrl: string;
  wsUrl: string;
  stop: () => void;
}

export type Envelope = {
  type: string;
  payload?: Record<string, unknown>;
  timestamp?: number;
  _tRecv: number;
};

export function startEvidenceServer(): EvidenceServer {
  const server = Bun.serve<WsData>({
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

  return {
    server,
    baseUrl: `http://127.0.0.1:${server.port}`,
    wsUrl: `ws://127.0.0.1:${server.port}/ws`,
    stop: () => {
      wsManager.clearConnections();
      server.stop(true);
    },
  };
}

export async function registerUser(
  name: string,
  email: string,
  password: string,
  deviceId: string
): Promise<AuthResult> {
  const api = new ApiRequest();
  api.setHeader("X-Device-Id", deviceId);
  api.setHeader("X-Device-Manufacturer", "Evidence");
  api.setHeader("X-Device-Model", "Harness");
  const res = await api.post("/api/auth/register", { name, email, password });
  const body = (await res.json()) as ApiSuccessResponse<AuthResult>;
  if (res.status !== 201 || !body.data?.token) {
    throw new Error(`register failed status=${res.status}`);
  }
  return body.data;
}

export class EvidenceSocket {
  readonly ws: WebSocket;
  readonly frames: Envelope[] = [];
  private cursor = 0;
  private waiters: Array<{
    predicate: (msg: Envelope) => boolean;
    resolve: (msg: Envelope) => void;
    timer: ReturnType<typeof setTimeout>;
  }> = [];

  constructor(ws: WebSocket) {
    this.ws = ws;
    ws.addEventListener("message", (event) => {
      const tRecv = performance.now();
      let parsed: { type: string; payload?: Record<string, unknown>; timestamp?: number };
      try {
        parsed = JSON.parse(String(event.data));
      } catch {
        return;
      }
      const envelope: Envelope = { ...parsed, _tRecv: tRecv };
      this.frames.push(envelope);
      const remaining: typeof this.waiters = [];
      let matched = false;
      for (const waiter of this.waiters) {
        if (!matched && waiter.predicate(envelope)) {
          matched = true;
          this.cursor = this.frames.length;
          clearTimeout(waiter.timer);
          waiter.resolve(envelope);
        } else {
          remaining.push(waiter);
        }
      }
      this.waiters = remaining;
    });
  }

  send(
    type: string,
    payload: Record<string, unknown> = {},
    extra: Record<string, unknown> = {}
  ): number {
    const tSend = performance.now();
    this.ws.send(
      JSON.stringify({
        type,
        timestamp: Date.now(),
        payload,
        ...extra,
      })
    );
    return tSend;
  }

  waitFor(type: string, timeoutMs = 2000): Promise<Envelope> {
    return this.waitUntil((msg) => msg.type === type, timeoutMs);
  }

  waitUntil(predicate: (msg: Envelope) => boolean, timeoutMs = 2000): Promise<Envelope> {
    const existing = this.takeFromBuffer(predicate);
    if (existing) return Promise.resolve(existing);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters = this.waiters.filter((waiter) => waiter.timer !== timer);
        reject(new Error(`timeout waiting for websocket message (${timeoutMs} ms)`));
      }, timeoutMs);
      this.waiters.push({ predicate, resolve, timer });
    });
  }

  private takeFromBuffer(predicate: (msg: Envelope) => boolean): Envelope | undefined {
    for (let index = this.cursor; index < this.frames.length; index++) {
      const frame = this.frames[index];
      if (frame && predicate(frame)) {
        this.cursor = index + 1;
        return frame;
      }
    }
    return undefined;
  }

  async expectNo(type: string, timeoutMs = 400): Promise<boolean> {
    try {
      await this.waitFor(type, timeoutMs);
      return false;
    } catch {
      return true;
    }
  }

  countType(type: string): number {
    return this.frames.filter((frame) => frame.type === type).length;
  }

  close(): void {
    this.ws.close();
  }
}

export function openWs(url: string, timeoutMs = 2000): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    const timer = setTimeout(() => reject(new Error("ws open timeout")), timeoutMs);
    ws.addEventListener("open", () => {
      clearTimeout(timer);
      resolve(ws);
    });
    ws.addEventListener("error", (error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

export async function connectSocket(
  wsUrl: string,
  token: string,
  deviceId: string
): Promise<EvidenceSocket> {
  const url =
    `${wsUrl}?token=${encodeURIComponent(token)}` +
    `&deviceInstanceId=${encodeURIComponent(deviceId)}` +
    `&manufacturer=Evidence&model=Harness&androidVersion=16`;
  const ws = await openWs(url);
  return new EvidenceSocket(ws);
}

export function uniqueSuffix(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}
