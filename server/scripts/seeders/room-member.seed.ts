import { and, eq } from "drizzle-orm";
import { db } from "../../src/db";
import { roomMembers } from "../../src/db/schema";
import { Seeder } from "./seed";

type RoomMemberModel = typeof roomMembers.$inferInsert;

export type RoomMemberSeedModel = RoomMemberModel;

export class RoomMemberSeeder extends Seeder {
  async single(data: RoomMemberSeedModel) {
    const [member] = await db.insert(roomMembers).values(data).onConflictDoNothing().returning();

    return member;
  }

  async multiple(data: RoomMemberSeedModel[]) {
    return Promise.all(data.map((member) => this.single(member)));
  }

  deleteByRoomCode(roomCode: string) {
    return db.delete(roomMembers).where(eq(roomMembers.roomCode, roomCode));
  }

  deleteByRoomCodeAndUserId(roomCode: string, userId: string) {
    return db
      .delete(roomMembers)
      .where(and(eq(roomMembers.roomCode, roomCode), eq(roomMembers.userId, userId)));
  }
}
