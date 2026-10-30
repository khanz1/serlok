import { expect, test, describe, afterEach } from "bun:test";
import { TestContext } from "../../helpers/db-cleanup";
import { ApiRequest } from "../../helpers/api.request";
import type { UserSeedModel } from "../../../scripts/seeders/user.seed";
import type { ApiFailResponse, ApiSuccessResponse } from "../../../src/utils/api-response";

const ctx = new TestContext();
const api = new ApiRequest();

const logoutUrl = "/api/auth/logout";
const user1: UserSeedModel = {
  name: "Naruto Uzumaki",
  email: "naruto.uzumaki@mail.com",
  password: "SayangHinat4!>.<",
};

afterEach(async () => ctx.cleanup());

describe("E2E: POST /api/auth/logout", () => {
  test("logout with valid token succeeds", async () => {
    const loginData = await api.login(user1);

    const res = await api.post(
      logoutUrl,
      {},
      { Authorization: `Bearer ${loginData.token}` }
    );

    expect(res.status).toBe(200);
    const body = (await res.json()) as ApiSuccessResponse<null>;
    expect(body.ok).toBe(true);
    expect(body.data).toBeNull();
  });

  test("logout without Authorization header fails", async () => {
    const res = await api.post(logoutUrl, {});

    expect(res.status).toBe(401);
    const body = (await res.json()) as ApiFailResponse;
    expect(body.error).toBe("Missing Authorization header");
  });

  test("removed refresh route returns 404", async () => {
    const res = await api.post("/api/auth/refresh", {});

    expect(res.status).toBe(404);
  });
});
