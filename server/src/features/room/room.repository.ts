import { and, eq, isNull } from "drizzle-orm";
import { db } from "../../db";
import { rooms, roomMembers } from "../../db/schema";

export type RoomRow = typeof rooms.$inferSelect;

export const ALLOWED_SHAKE_THRESHOLDS = [2.5, 3.5, 4.5] as const;
export const ALLOWED_LOCATION_INTERVALS_MS = [2_000, 5_000, 10_000, 30_000, 60_000] as const;

export class RoomRepository {
  async create(
    code: string,
    masterId: string,
    options?: { shakeThreshold?: number; locationIntervalMs?: number }
  ): Promise<RoomRow> {
    const [row] = await db
      .insert(rooms)
      .values({
        code,
        masterId,
        isSharing: false,
        shakeThreshold: options?.shakeThreshold ?? 3.5,
        locationIntervalMs: options?.locationIntervalMs ?? 10_000,
      })
      .returning();
    return row!;
  }

  async findByCode(code: string): Promise<RoomRow | undefined> {
    return db.query.rooms.findFirst({ where: eq(rooms.code, code) });
  }

  async updateSharing(code: string, isSharing: boolean): Promise<void> {
    await db.update(rooms).set({ isSharing }).where(eq(rooms.code, code));
  }

  async updateSettings(
    code: string,
    settings: { shakeThreshold: number; locationIntervalMs: number }
  ): Promise<void> {
    await db
      .update(rooms)
      .set({
        shakeThreshold: settings.shakeThreshold,
        locationIntervalMs: settings.locationIntervalMs,
      })
      .where(and(eq(rooms.code, code), isNull(rooms.closedAt)));
  }

  async updateShakeThreshold(code: string, shakeThreshold: number): Promise<void> {
    await db.update(rooms).set({ shakeThreshold }).where(eq(rooms.code, code));
  }

  async softCloseRoom(code: string, closedAt: Date = new Date()): Promise<void> {
    await db.update(rooms).set({ isSharing: false, closedAt }).where(eq(rooms.code, code));
  }

  async deleteRoom(code: string): Promise<void> {
    await db.delete(rooms).where(eq(rooms.code, code));
  }

  async addMember(roomCode: string, userId: string): Promise<void> {
    await db.insert(roomMembers).values({ roomCode, userId }).onConflictDoNothing();
  }

  async removeMember(roomCode: string, userId: string): Promise<void> {
    await db
      .delete(roomMembers)
      .where(and(eq(roomMembers.roomCode, roomCode), eq(roomMembers.userId, userId)));
  }

  async clearMembers(roomCode: string): Promise<void> {
    await db.delete(roomMembers).where(eq(roomMembers.roomCode, roomCode));
  }

  async getMembers(roomCode: string): Promise<string[]> {
    const rows = await db
      .select({ userId: roomMembers.userId })
      .from(roomMembers)
      .where(eq(roomMembers.roomCode, roomCode));
    return rows.map((r) => r.userId);
  }

  async findOpenRoomForUser(userId: string): Promise<RoomRow | undefined> {
    const rows = await db
      .select({ room: rooms })
      .from(roomMembers)
      .innerJoin(rooms, eq(roomMembers.roomCode, rooms.code))
      .where(and(eq(roomMembers.userId, userId), isNull(rooms.closedAt)))
      .limit(1);
    return rows[0]?.room;
  }
}

export const roomRepository = new RoomRepository();
