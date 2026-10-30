import { describe, expect, test } from "bun:test";
import {
  AppError,
  BadRequestError,
  ForbiddenError,
  InternalServerError,
  NotFoundError,
  UnauthorizedError,
} from "../../../src/utils/error";

describe("AppError", () => {
  test("stores message and status code", () => {
    const error = new AppError("Custom error", 418);

    expect(error).toBeInstanceOf(Error);
    expect(error.message).toBe("Custom error");
    expect(error.statusCode).toBe(418);
  });
});

describe("HTTP errors", () => {
  test("BadRequestError uses status code 400", () => {
    const error = new BadRequestError("Bad request");

    expect(error).toBeInstanceOf(AppError);
    expect(error.message).toBe("Bad request");
    expect(error.statusCode).toBe(400);
  });

  test("UnauthorizedError uses status code 401", () => {
    const error = new UnauthorizedError("Unauthorized");

    expect(error).toBeInstanceOf(AppError);
    expect(error.message).toBe("Unauthorized");
    expect(error.statusCode).toBe(401);
  });

  test("ForbiddenError uses status code 403", () => {
    const error = new ForbiddenError("Forbidden");

    expect(error).toBeInstanceOf(AppError);
    expect(error.message).toBe("Forbidden");
    expect(error.statusCode).toBe(403);
  });

  test("NotFoundError uses status code 404", () => {
    const error = new NotFoundError("Not found");

    expect(error).toBeInstanceOf(AppError);
    expect(error.message).toBe("Not found");
    expect(error.statusCode).toBe(404);
  });

  test("InternalServerError uses status code 500", () => {
    const error = new InternalServerError("Internal server error");

    expect(error).toBeInstanceOf(AppError);
    expect(error.message).toBe("Internal server error");
    expect(error.statusCode).toBe(500);
  });
});
