import { eq } from "drizzle-orm";
import { db } from "../../db";
import { users } from "../../db/schema";

export type UserRow = typeof users.$inferSelect;
export type NewUser = typeof users.$inferInsert;

export class AuthRepository {
  toPublicUser(row: UserRow) {
    return {
      id: row.id,
      name: row.name,
      email: row.email,
      createdAt: row.createdAt,
    };
  }

  async findByEmail(email: string): Promise<UserRow | undefined> {
    return db.query.users.findFirst({
      where: eq(users.email, email.toLowerCase()),
    });
  }

  async findById(id: string): Promise<UserRow | undefined> {
    return db.query.users.findFirst({ where: eq(users.id, id) });
  }

  async create(data: NewUser): Promise<UserRow> {
    const [row] = await db.insert(users).values(data).returning();
    return row!;
  }
}
