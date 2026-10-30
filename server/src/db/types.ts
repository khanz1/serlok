export interface UserRow {
  id: string;
  name: string;
  email: string;
  passwordHash: string;
  createdAt: Date;
}

export type NewUser = {
  id?: string;
  name: string;
  email: string;
  passwordHash: string;
  createdAt?: Date;
};

export interface DeviceRow {
  userId: string;
  deviceInstanceId: string;
  manufacturer: string;
  model: string;
  androidVersion: string;
  lastSeenAt: Date;
  createdAt: Date;
  updatedAt: Date;
}

export type NewDevice = {
  userId: string;
  deviceInstanceId: string;
  manufacturer: string;
  model: string;
  androidVersion: string;
  lastSeenAt?: Date;
  createdAt?: Date;
  updatedAt?: Date;
};

export interface RoomRow {
  code: string;
  masterId: string;
  isSharing: boolean;
  shakeThreshold: number;
  locationIntervalMs: number;
  closedAt: Date | null;
  createdAt: Date;
}

export type NewRoom = {
  code: string;
  masterId: string;
  isSharing?: boolean;
  shakeThreshold?: number;
  locationIntervalMs?: number;
  closedAt?: Date | null;
  createdAt?: Date;
};

export interface RoomMemberRow {
  roomCode: string;
  userId: string;
  joinedAt: Date;
}

export type NewRoomMember = {
  roomCode: string;
  userId: string;
  joinedAt?: Date;
};

export interface SosEventRow {
  id: string;
  roomCode: string;
  userId: string;
  idempotencyKey: string;
  magnitude: number;
  x: number;
  y: number;
  z: number;
  latitude: number | null;
  longitude: number | null;
  accuracy: number | null;
  createdAt: Date;
  updatedAt: Date;
}

export type NewSosEvent = {
  id?: string;
  roomCode: string;
  userId: string;
  idempotencyKey?: string;
  magnitude: number;
  x: number;
  y: number;
  z: number;
  latitude?: number | null;
  longitude?: number | null;
  accuracy?: number | null;
  createdAt?: Date;
  updatedAt?: Date;
};
