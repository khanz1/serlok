import { expect, test, describe, afterEach } from "bun:test";
import { TestContext } from "../../helpers/db-cleanup";
import { ApiRequest } from "../../helpers/api.request";
import { UserSeeder, type UserSeedModel } from "../../../scripts/seeders/user.seed";
import type { ApiFailResponse, ApiSuccessResponse } from "../../../src/utils/api-response";
import type { AuthResult } from "../../../src/features/auth/auth.type";

const ctx = new TestContext();
const api = new ApiRequest();
const userSeeder = new UserSeeder();

const apiUrl = "/api/auth/login";
const user1: UserSeedModel = {
  name: "Naruto Uzumaki",
  email: "naruto.uzumaki@mail.com",
  password: "SayangHinat4!>.<",
};

const user2: UserSeedModel = {
  name: "Sasuke Uchiha",
  email: "sasuke.uchiha@mail.com",
  password: "Uchiha4Life!@#",
};

const incorrectPassword = "WrongPass123";
const invalidEmail = "kakashi.hatake";

afterEach(async () => ctx.cleanup());

describe("E2E: POST /api/auth/login", () => {
  test("complete login flow with correct credentials", async () => {
    await userSeeder.single(user1);

    const res = await api.post(apiUrl, {
      email: user1.email,
      password: user1.password,
    });

    expect(res.status).toBe(200);
    const body = (await res.json()) as ApiSuccessResponse<AuthResult>;

    expect(body.data.token).toBeDefined();
    expect("refreshToken" in body.data).toBe(false);
    expect(body.data.user).toBeDefined();
    expect(body.data.user.email).toBe(user1.email);
    expect(body.data.user.name).toBe(user1.name);
  });

  test("login without device headers succeeds", async () => {
    await userSeeder.single(user1);

    const request = new ApiRequest();
    const res = await request.post(apiUrl, {
      email: user1.email,
      password: user1.password,
    });

    expect(res.status).toBe(200);
    const body = (await res.json()) as ApiSuccessResponse<AuthResult>;
    expect(body.data.token).toBeDefined();
  });

  test("login with wrong password fails", async () => {
    await userSeeder.single(user1);

    const res = await api.post(apiUrl, {
      email: user1.email,
      password: incorrectPassword,
    });

    expect(res.status).toBe(400);
    const body = (await res.json()) as ApiFailResponse;
    expect(body.error).toBe("Invalid email or password");
  });

  test("login with non-existent email fails", async () => {
    const res = await api.post(apiUrl, {
      email: user2.email,
      password: user2.password,
    });

    expect(res.status).toBe(400);
    const body = (await res.json()) as ApiFailResponse;
    expect(body.error).toBe("Invalid email or password");
  });

  test("login with invalid email format fails", async () => {
    const res = await api.post(apiUrl, {
      email: invalidEmail,
      password: user2.password,
    });

    expect(res.status).toBe(400);
  });

  test("login with empty password fails", async () => {
    await userSeeder.single(user1);

    const res = await api.post(apiUrl, {
      email: user1.email,
      password: "",
    });

    expect(res.status).toBe(400);
  });
});
