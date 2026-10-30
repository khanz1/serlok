import { expect, test, describe, afterEach } from "bun:test";
import { TestContext } from "../../helpers/db-cleanup";
import { ApiRequest } from "../../helpers/api.request";
import { UserSeeder, type UserSeedModel } from "../../../scripts/seeders/user.seed";
import type { ApiFailResponse, ApiSuccessResponse } from "../../../src/utils/api-response";
import type { AuthResult } from "../../../src/features/auth/auth.type";

const ctx = new TestContext();
const api = new ApiRequest();
const userSeeder = new UserSeeder();

const apiUrl = "/api/auth/register";
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

const invalidEmail = "kakashi.hatake";

afterEach(async () => ctx.cleanup());

describe("E2E: POST /api/auth/register", () => {
  test("complete registration flow with valid data", async () => {
    const res = await api.post(apiUrl, user1);

    expect(res.status).toBe(201);
    const body = (await res.json()) as ApiSuccessResponse<AuthResult>;

    expect(body.data.token).toBeDefined();
    expect("refreshToken" in body.data).toBe(false);
    expect(body.data.user).toBeDefined();
    expect(body.data.user.id).toBeDefined();
    expect(body.data.user.name).toBe(user1.name);
    expect(body.data.user.email).toBe(user1.email);
    expect(body.data.user.createdAt).toBeDefined();
    expect("passwordHash" in body.data.user).toBe(false);
  });

  test("registration without device headers succeeds", async () => {
    const request = new ApiRequest();
    const res = await request.post(apiUrl, user1);

    expect(res.status).toBe(201);
    const body = (await res.json()) as ApiSuccessResponse<AuthResult>;
    expect(body.data.token).toBeDefined();
  });

  test("duplicate email registration fails", async () => {
    await userSeeder.single(user1);

    const res = await api.post(apiUrl, {
      name: user2.name,
      email: user1.email,
      password: user2.password,
    });

    expect(res.status).toBe(400);
    const body = (await res.json()) as ApiFailResponse;
    expect(body.error).toBe("Email already registered");
  });

  test("registration with invalid email format fails", async () => {
    const res = await api.post(apiUrl, {
      name: user1.name,
      email: invalidEmail,
      password: user1.password,
    });

    expect(res.status).toBe(400);
  });

  test("registration with weak password fails", async () => {
    const res = await api.post(apiUrl, {
      name: user1.name,
      email: user1.email,
      password: "weak",
    });

    expect(res.status).toBe(400);
  });

  test("registration with empty name fails", async () => {
    const res = await api.post(apiUrl, {
      name: "",
      email: user1.email,
      password: user1.password,
    });

    expect(res.status).toBe(400);
  });

  test("registration with name exceeding 50 characters fails", async () => {
    const res = await api.post(apiUrl, {
      name: "A".repeat(51),
      email: user1.email,
      password: user1.password,
    });

    expect(res.status).toBe(400);
  });
});
