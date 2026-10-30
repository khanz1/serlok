import { eq } from "drizzle-orm";
import { db } from "../../src/db";
import { rooms } from "../../src/db/schema";
import { Seeder } from "./seed";

type RoomModel = typeof rooms.$inferInsert;

export type RoomSeedModel = RoomModel;

export class RoomSeeder extends Seeder {
  async single(data: RoomSeedModel) {
    const [room] = await db
      .insert(rooms)
      .values(data)
      .onConflictDoUpdate({
        target: rooms.code,
        set: {
          masterId: data.masterId,
          isSharing: data.isSharing,
        },
      })
      .returning();

    return room!;
  }

  async multiple(data: RoomSeedModel[]) {
    return Promise.all(data.map((room) => this.single(room)));
  }

  deleteByCode(code: string) {
    return db.delete(rooms).where(eq(rooms.code, code));
  }
}
