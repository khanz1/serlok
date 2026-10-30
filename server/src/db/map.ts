import type { DeviceRow, RoomMemberRow, RoomRow, SosEventRow, UserRow } from "./types";

function asRecord(row: unknown): Record<string, unknown> {
  return row as Record<string, unknown>;
}

function pick(row: unknown, camel: string, snake: string): unknown {
  const record = asRecord(row);
  if (camel in record && record[camel] !== undefined) {
    return record[camel];
  }
  return record[snake];
}

function asString(value: unknown): string {
  return String(value);
}

function asNumber(value: unknown): number {
  return Number(value);
}

function asNumberOrNull(value: unknown): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  return Number(value);
}

function asBoolean(value: unknown): boolean {
  return value === true || value === "t" || value === "true";
}

function asDate(value: unknown): Date {
  if (value instanceof Date) {
    return value;
  }
  return new Date(String(value));
}

function asDateOrNull(value: unknown): Date | null {
  if (value === null || value === undefined) {
    return null;
  }
  return asDate(value);
}

export function first<T>(rows: T[]): T | undefined {
  return rows[0];
}

export function toUser(row: unknown): UserRow {
  return {
    id: asString(pick(row, "id", "id")),
    name: asString(pick(row, "name", "name")),
    email: asString(pick(row, "email", "email")),
    passwordHash: asString(pick(row, "passwordHash", "password_hash")),
    createdAt: asDate(pick(row, "createdAt", "created_at")),
  };
}

export function toDevice(row: unknown): DeviceRow {
  return {
    userId: asString(pick(row, "userId", "user_id")),
    deviceInstanceId: asString(pick(row, "deviceInstanceId", "device_instance_id")),
    manufacturer: asString(pick(row, "manufacturer", "manufacturer")),
    model: asString(pick(row, "model", "model")),
    androidVersion: asString(pick(row, "androidVersion", "android_version")),
    lastSeenAt: asDate(pick(row, "lastSeenAt", "last_seen_at")),
    createdAt: asDate(pick(row, "createdAt", "created_at")),
    updatedAt: asDate(pick(row, "updatedAt", "updated_at")),
  };
}

export function toRoom(row: unknown): RoomRow {
  return {
    code: asString(pick(row, "code", "code")),
    masterId: asString(pick(row, "masterId", "master_id")),
    isSharing: asBoolean(pick(row, "isSharing", "is_sharing")),
    shakeThreshold: asNumber(pick(row, "shakeThreshold", "shake_threshold")),
    locationIntervalMs: asNumber(pick(row, "locationIntervalMs", "location_interval_ms")),
    closedAt: asDateOrNull(pick(row, "closedAt", "closed_at")),
    createdAt: asDate(pick(row, "createdAt", "created_at")),
  };
}

export function toRoomMember(row: unknown): RoomMemberRow {
  return {
    roomCode: asString(pick(row, "roomCode", "room_code")),
    userId: asString(pick(row, "userId", "user_id")),
    joinedAt: asDate(pick(row, "joinedAt", "joined_at")),
  };
}

export function toSosEvent(row: unknown): SosEventRow {
  return {
    id: asString(pick(row, "id", "id")),
    roomCode: asString(pick(row, "roomCode", "room_code")),
    userId: asString(pick(row, "userId", "user_id")),
    idempotencyKey: asString(pick(row, "idempotencyKey", "idempotency_key")),
    magnitude: asNumber(pick(row, "magnitude", "magnitude")),
    x: asNumber(pick(row, "x", "x")),
    y: asNumber(pick(row, "y", "y")),
    z: asNumber(pick(row, "z", "z")),
    latitude: asNumberOrNull(pick(row, "latitude", "latitude")),
    longitude: asNumberOrNull(pick(row, "longitude", "longitude")),
    accuracy: asNumberOrNull(pick(row, "accuracy", "accuracy")),
    createdAt: asDate(pick(row, "createdAt", "created_at")),
    updatedAt: asDate(pick(row, "updatedAt", "updated_at")),
  };
}
