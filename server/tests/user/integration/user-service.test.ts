import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { UserSeeder } from "../../../scripts/seeders/user.seed";
import { UserRepository } from "../../../src/features/user/user.repository";
import { UserService } from "../../../src/features/user/user.service";
import { TestContext } from "../../helpers/db-cleanup";

const ctx = new TestContext();
const users = new UserSeeder();

describe("UserService getMe", () => {
  beforeEach(() => ctx.cleanup());
  afterEach(() => ctx.cleanup());

  test("returns the public profile for an existing user", async () => {
    const owner = await users.single({
      name: "Owner",
      email: "owner-me@example.com",
      password: "PassPass123",
    });
    const service = new UserService(new UserRepository());

    const me = await service.getMe(owner.id);

    expect(me).toMatchObject({
      id: owner.id,
      name: owner.name,
      email: owner.email,
    });
  });
});
