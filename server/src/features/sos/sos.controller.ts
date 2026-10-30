// crypto: uses global crypto.randomUUID
import type { ServerWebSocket } from "bun";
import { z } from "zod";
import { ts } from "../../utils/log";
import type { WsData } from "../ws/ws.types";
import { AlertCooldown } from "../ws/throttle";
import { short, sendError } from "../ws/ws.manager";
import { roomService as defaultRoomService } from "../room/room.service";
import { sosRepository as defaultSosRepository } from "./sos.repository";
import type { SosRepository } from "./sos.repository";

interface SosRoomService {
  getRoom(
    code: string
  ): Promise<
    { isSharing: boolean; shakeThreshold: number; code?: string; masterId?: string } | undefined
  >;
  broadcastToRoom(code: string, payload: string, excludeUserId?: string): void;
  getLastLocation(
    roomCode: string,
    userId: string
  ): { latitude: number; longitude: number; accuracy?: number | null } | undefined;
}

interface SosRepositoryPort {
  create: SosRepository["create"];
}

const ShakePayloadSchema = z
  .object({
    magnitudeG: z.number().finite().nonnegative(),
    x: z.number().finite(),
    y: z.number().finite(),
    z: z.number().finite(),
  })
  .strict();

export interface SosControllerDeps {
  roomService?: SosRoomService;
  sosRepository?: SosRepositoryPort;
  cooldown?: AlertCooldown;
  now?: () => number;
  uuid?: () => string;
}

export class SosController {
  private readonly roomService: SosRoomService;
  private readonly sosRepository: SosRepositoryPort;
  private readonly cooldown: AlertCooldown;
  private readonly now: () => number;
  private readonly uuid: () => string;

  constructor(deps: SosControllerDeps = {}) {
    this.roomService = deps.roomService ?? defaultRoomService;
    this.sosRepository = deps.sosRepository ?? defaultSosRepository;
    this.cooldown = deps.cooldown ?? new AlertCooldown(3_000);
    this.now = deps.now ?? Date.now;
    this.uuid = deps.uuid ?? crypto.randomUUID.bind(crypto);

    this.handleSosTriggerViaWs = this.handleSosTriggerViaWs.bind(this);
    this.cleanupSosDevice = this.cleanupSosDevice.bind(this);
  }

  cleanupSosDevice(userId: string): void {
    this.cooldown.delete(userId);
  }

  async handleSosTriggerViaWs(
    ws: ServerWebSocket<WsData>,
    payload: Record<string, unknown>,
    raw: string
  ): Promise<void> {
    const { userId } = ws.data;
    const roomCode = ws.data.roomCode;
    if (!roomCode) return;

    const room = await this.roomService.getRoom(roomCode);
    if (!room?.isSharing) return;

    const now = this.now();
    const envelope = parseEnvelope(raw);
    const parsedPayload = ShakePayloadSchema.safeParse(payload);
    if (!parsedPayload.success) {
      sendError(ws, "Invalid shake SOS payload");
      return;
    }

    const clientTimestamp = typeof envelope.timestamp === "number" ? envelope.timestamp : now;
    if (Math.abs(now - clientTimestamp) > 30_000) {
      sendError(ws, "Stale SOS trigger");
      return;
    }

    const { magnitudeG, x, y, z } = parsedPayload.data;
    if (magnitudeG < room.shakeThreshold) return;

    if (!this.cooldown.shouldFire(userId, now)) return;

    const requestedIdempotency =
      typeof envelope.idempotencyKey === "string" ? envelope.idempotencyKey : undefined;
    if (requestedIdempotency && !isUuid(requestedIdempotency)) {
      sendError(ws, "Invalid SOS idempotency key");
      return;
    }
    const idempotencyKey =
      requestedIdempotency && isUuid(requestedIdempotency) ? requestedIdempotency : this.uuid();

    const location = this.roomService.getLastLocation(roomCode, userId);
    const { event, created } = await this.sosRepository.create({
      roomCode,
      userId,
      idempotencyKey,
      magnitude: magnitudeG,
      x,
      y,
      z,
      latitude: location?.latitude ?? null,
      longitude: location?.longitude ?? null,
      accuracy: location?.accuracy ?? null,
    });
    if (!created) return;

    this.roomService.broadcastToRoom(
      roomCode,
      JSON.stringify({
        type: "room_alert",
        payload: {
          incidentId: event.id,
          userId,
          name: ws.data.name,
          magnitudeG,
          location: location ?? null,
          triggeredAt: event.createdAt.toISOString(),
        },
        timestamp: now,
      })
    );

    console.log(
      `${ts()} ALERT ${short(userId).padEnd(12)} room=${roomCode} event=${short(event.id)}`
    );
  }
}

function parseEnvelope(raw: string): Record<string, unknown> {
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
