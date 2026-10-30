import { expect, test, describe, mock } from "bun:test";
import { TokenUtils } from "../../../src/features/auth/token.utils";
import type { AuthRepository } from "../../../src/features/auth/auth.repository";
import type { IPublicUser } from "../../../src/features/auth/auth.type";

describe("TokenUtils", () => {
  const mockAuthRepository: AuthRepository = {
    findById: mock(() => Promise.resolve(undefined)),
  } as any;

  const mockUser: IPublicUser = {
    id: "user-123",
    email: "test@example.com",
    name: "Test User",
    createdAt: new Date(),
  };

  const tokenUtils = new TokenUtils(mockAuthRepository);

  describe("signToken", () => {
    test("signs a session token with user data", async () => {
      const token = await tokenUtils.signToken(mockUser.id, mockUser.email, mockUser.name);

      expect(token).toBeDefined();
      expect(typeof token).toBe("string");
      expect(token.split(".")).toHaveLength(3);
    });

    test("token contains correct user payload", async () => {
      const token = await tokenUtils.signToken(mockUser.id, mockUser.email, mockUser.name);
      const parts = token.split(".");
      const payload = JSON.parse(Buffer.from(parts[1]!, "base64").toString());

      expect(payload.sub).toBe(mockUser.id);
      expect(payload.email).toBe(mockUser.email);
      expect(payload.name).toBe(mockUser.name);
    });

    test("token expires after one day", async () => {
      const now = Math.floor(Date.now() / 1000);
      const token = await tokenUtils.signToken(mockUser.id, mockUser.email, mockUser.name);
      const parts = token.split(".");
      const payload = JSON.parse(Buffer.from(parts[1]!, "base64").toString());
      const oneDay = 60 * 60 * 24;

      expect(payload.exp).toBeGreaterThanOrEqual(now + oneDay - 5);
      expect(payload.exp).toBeLessThanOrEqual(now + oneDay + 5);
    });
  });

  describe("validateToken", () => {
    test("returns user for valid token", async () => {
      (mockAuthRepository.findById as any).mockResolvedValueOnce(mockUser);

      const token = await tokenUtils.signToken(mockUser.id, mockUser.email, mockUser.name);
      const user = await tokenUtils.validateToken(token);

      expect(user).toEqual(mockUser);
      expect(mockAuthRepository.findById).toHaveBeenCalledWith(mockUser.id);
    });

    test("returns undefined for invalid token", async () => {
      const user = await tokenUtils.validateToken("invalid-token");

      expect(user).toBeUndefined();
    });

    test("returns undefined when user not found", async () => {
      (mockAuthRepository.findById as any).mockResolvedValueOnce(undefined);

      const token = await tokenUtils.signToken(mockUser.id, mockUser.email, mockUser.name);
      const user = await tokenUtils.validateToken(token);

      expect(user).toBeUndefined();
    });
  });

  describe("issueToken", () => {
    test("issues a session token with the public user", async () => {
      const result = await tokenUtils.issueToken(mockUser);

      expect(result.token).toBeDefined();
      expect(result.token.split(".")).toHaveLength(3);
      expect(result.user).toEqual(mockUser);
      expect("refreshToken" in result).toBe(false);
    });
  });
});
