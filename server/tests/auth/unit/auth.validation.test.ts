import { expect, test, describe } from "bun:test";
import { RegisterSchema, LoginSchema } from "../../../src/features/auth/auth.validation";

describe("RegisterSchema", () => {
  test("accepts valid name/email/password", () => {
    const result = RegisterSchema.safeParse({
      name: "Alice",
      email: "alice@example.com",
      password: "securepassword",
    });
    expect(result.success).toBe(true);
  });

  test("rejects empty name (min 1)", () => {
    const result = RegisterSchema.safeParse({
      name: "",
      email: "alice@example.com",
      password: "securepassword",
    });
    expect(result.success).toBe(false);
  });

  test("rejects name longer than 50 characters", () => {
    const result = RegisterSchema.safeParse({
      name: "A".repeat(51),
      email: "alice@example.com",
      password: "securepassword",
    });
    expect(result.success).toBe(false);
  });

  test("accepts name exactly 50 characters", () => {
    const result = RegisterSchema.safeParse({
      name: "A".repeat(50),
      email: "alice@example.com",
      password: "securepassword",
    });
    expect(result.success).toBe(true);
  });

  test("rejects invalid email format", () => {
    const result = RegisterSchema.safeParse({
      name: "Alice",
      email: "not-an-email",
      password: "securepassword",
    });
    expect(result.success).toBe(false);
  });

  test("rejects password shorter than 8 characters", () => {
    const result = RegisterSchema.safeParse({
      name: "Alice",
      email: "alice@example.com",
      password: "short",
    });
    expect(result.success).toBe(false);
  });

  test("accepts password exactly 8 characters", () => {
    const result = RegisterSchema.safeParse({
      name: "Alice",
      email: "alice@example.com",
      password: "12345678",
    });
    expect(result.success).toBe(true);
  });
});

describe("LoginSchema", () => {
  test("accepts valid email and password", () => {
    const result = LoginSchema.safeParse({
      email: "alice@example.com",
      password: "anypassword",
    });
    expect(result.success).toBe(true);
  });

  test("rejects invalid email format", () => {
    const result = LoginSchema.safeParse({
      email: "notanemail",
      password: "anypassword",
    });
    expect(result.success).toBe(false);
  });

  test("rejects empty password (min 1)", () => {
    const result = LoginSchema.safeParse({
      email: "alice@example.com",
      password: "",
    });
    expect(result.success).toBe(false);
  });

  test("rejects missing fields", () => {
    const result = LoginSchema.safeParse({});
    expect(result.success).toBe(false);
  });
});
