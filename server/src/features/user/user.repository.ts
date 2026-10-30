import { eq } from "drizzle-orm";
import { db } from "../../db";
import { users } from "../../db/schema";
import type { UserRow } from "../auth/auth.repository";

export class UserRepository {
  async findById(id: string): Promise<UserRow | undefined> {
    return db.query.users.findFirst({ where: eq(users.id, id) });
  }
}
