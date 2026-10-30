import { expect, test, describe, beforeEach, afterEach } from "bun:test";
import { TestContext } from "../../helpers/db-cleanup";
import { ApiRequest } from "../../helpers/api.request";
import { type UserSeedModel } from "../../../scripts/seeders/user.seed";
import type { AuthResult, IPublicUser } from "../../../src/features/auth/auth.type";
import type { ApiSuccessResponse } from "../../../src/utils/api-response";

const ctx = new TestContext();
let api: ApiRequest;
let loggedInApi: ApiRequest;

const meUrl = "/api/users/me";
let loginData: AuthResult;

const user1: UserSeedModel = {
  name: "Naruto Uzumaki",
  email: "naruto.uzumaki@mail.com",
  password: "SayangHinat4!>.<",
};

beforeEach(async () => {
  api = new ApiRequest();
  loggedInApi = new ApiRequest();

  api.setHeader("X-Device-Id", "test-device-id");
  api.setHeader("X-Device-Manufacturer", "test-manufacturer");
  api.setHeader("X-Device-Model", "test-model");

  loginData = await api.login(user1);

  loggedInApi.setHeader("Authorization", `Bearer ${loginData.token}`);
});

afterEach(async () => ctx.cleanup());

describe("E2E: GET /api/users/me", () => {
  test("returns current user with valid token", async () => {
    const res = await loggedInApi.get(meUrl);

    expect(res.status).toBe(200);
    const body = (await res.json()) as ApiSuccessResponse<IPublicUser>;

    expect(body.data.id).toBe(loginData.user.id);
    expect(body.data.name).toBe(user1.name);
    expect(body.data.email).toBe(user1.email);
    expect(body.data.createdAt).toBeDefined();
  });

  test("fails with missing Authorization header", async () => {
    const res = await api.get(meUrl);
    expect(res.status).toBe(401);
  });

  test("fails with malformed token", async () => {
    const res = await api.get(meUrl, {
      Authorization: "Bearer not-a-real-token",
    });
    expect(res.status).toBe(401);
  });

  test("fails with empty bearer token", async () => {
    const res = await api.get(meUrl, {
      Authorization: "Bearer ",
    });
    expect(res.status).toBe(401);
  });
});

describe("E2E: DELETE /api/users/me", () => {
  test("is not exposed", async () => {
    expect((await loggedInApi.deleteJson(meUrl, { confirmation: "DELETE" })).status).toBe(404);
    expect((await loggedInApi.get(meUrl)).status).toBe(200);
  });
});
