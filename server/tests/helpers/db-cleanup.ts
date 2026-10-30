import { db } from "../../src/db/index";
import { users, devices, rooms, roomMembers, sosEvents } from "../../src/db/schema";

export class TestContext {
  async cleanup(): Promise<void> {
    await db.delete(sosEvents);
    await db.delete(roomMembers);
    await db.delete(rooms);
    await db.delete(devices);
    await db.delete(users);
  }
}
