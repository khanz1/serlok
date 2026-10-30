import { eq } from "drizzle-orm";
import { db } from "../../src/db";
import { users } from "../../src/db/schema";
import { Seeder } from "./seed";

type UserModel = typeof users.$inferInsert;

export type UserSeedModel = Omit<UserModel, "passwordHash"> & {
  password: string;
};

export const singleUserSeed: UserSeedModel = {
  name: "Senku Ishigami",
  email: "senku.ishigami@mail.com",
  password: "CorrectPass123",
};

export const multipleUserSeeds: UserSeedModel[] = [
  singleUserSeed,
  {
    name: "Oreki Hotarou",
    email: "oreki.hotarou@mail.com",
    password: "CorrectPass123",
  },
  {
    name: "Makise Kurisu",
    email: "makise.kurisu@mail.com",
    password: "CorrectPass123",
  },
];

export class UserSeeder extends Seeder {
  async toModel(data: UserSeedModel): Promise<UserModel> {
    const { password, email, ...user } = data;

    return {
      ...user,
      email: email.toLowerCase(),
      passwordHash: await Bun.password.hash(password),
    };
  }

  async single(data: UserSeedModel = singleUserSeed) {
    const userModel = await this.toModel(data);

    const [user] = await db
      .insert(users)
      .values(userModel)
      .onConflictDoUpdate({
        target: users.email,
        set: {
          name: userModel.name,
          passwordHash: userModel.passwordHash,
        },
      })
      .returning();

    return user!;
  }

  async multiple(data: UserSeedModel[] = multipleUserSeeds) {
    return Promise.all(data.map((user) => this.single(user)));
  }

  deleteById(id: string) {
    return db.delete(users).where(eq(users.id, id));
  }
}
