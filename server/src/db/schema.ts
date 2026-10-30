import {
  pgTable,
  uuid,
  text,
  char,
  boolean,
  timestamp,
  real,
  integer,
  primaryKey,
  uniqueIndex,
  check,
} from "drizzle-orm/pg-core";
import { sql } from "drizzle-orm";

export const users = pgTable("users", {
  id: uuid("id").primaryKey().defaultRandom(),
  name: text("name").notNull(),
  email: text("email").notNull().unique(),
  passwordHash: text("password_hash").notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const devices = pgTable(
  "devices",
  {
    userId: uuid("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    deviceInstanceId: text("device_instance_id").notNull(),
    manufacturer: text("manufacturer").notNull(),
    model: text("model").notNull(),
    androidVersion: text("android_version").notNull(),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true }).notNull().defaultNow(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({ pk: primaryKey({ columns: [t.userId, t.deviceInstanceId] }) })
);

export const LOCATION_INTERVAL_MS = [2_000, 5_000, 10_000, 30_000, 60_000] as const;

export const rooms = pgTable(
  "rooms",
  {
    code: char("code", { length: 6 }).primaryKey(),
    masterId: uuid("master_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    isSharing: boolean("is_sharing").notNull().default(false),
    shakeThreshold: real("shake_threshold").notNull().default(3.5),
    locationIntervalMs: integer("location_interval_ms").notNull().default(10_000),
    closedAt: timestamp("closed_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({
    shakeThresholdCheck: check(
      "rooms_shake_threshold_check",
      sql`${t.shakeThreshold} IN (2.5, 3.5, 4.5)`
    ),
    locationIntervalCheck: check(
      "rooms_location_interval_ms_check",
      sql`${t.locationIntervalMs} IN (2000, 5000, 10000, 30000, 60000)`
    ),
  })
);

export const roomMembers = pgTable(
  "room_members",
  {
    roomCode: char("room_code", { length: 6 })
      .notNull()
      .references(() => rooms.code, { onDelete: "cascade" }),
    userId: uuid("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    joinedAt: timestamp("joined_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({ pk: primaryKey({ columns: [t.roomCode, t.userId] }) })
);

export const sosEvents = pgTable(
  "sos_events",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    roomCode: char("room_code", { length: 6 }).notNull(),
    userId: uuid("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    idempotencyKey: uuid("idempotency_key").notNull().defaultRandom(),
    magnitude: real("magnitude").notNull(),
    x: real("x").notNull(),
    y: real("y").notNull(),
    z: real("z").notNull(),
    latitude: real("latitude"),
    longitude: real("longitude"),
    accuracy: real("accuracy"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({
    ownerIdempotency: uniqueIndex("sos_events_owner_idempotency_idx").on(
      t.userId,
      t.idempotencyKey
    ),
    coordinatesCheck: check(
      "sos_events_coordinates_check",
      sql`(${t.latitude} IS NULL OR ${t.latitude} BETWEEN -90 AND 90)
          AND (${t.longitude} IS NULL OR ${t.longitude} BETWEEN -180 AND 180)`
    ),
    magnitudeCheck: check("sos_events_magnitude_check", sql`${t.magnitude} >= 0`),
  })
);
