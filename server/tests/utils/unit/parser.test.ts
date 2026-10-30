import { describe, expect, test } from "bun:test";
import { z } from "zod";
import { RequestParser } from "../../../src/utils/parser";
import { BadRequestError } from "../../../src/utils/error";

const schema = z.object({
  name: z.string().min(1, "Name is required"),
});

describe("RequestParser", () => {
  test("parses valid request body", async () => {
    const req = {
      json: async () => ({ name: "Naruto" }),
    };

    const result = await RequestParser.body(req as never, schema);

    expect(result).toEqual({ name: "Naruto" });
  });

  test("throws BadRequestError with zod message for invalid body", async () => {
    const req = {
      json: async () => ({ name: "" }),
    };

    try {
      await RequestParser.body(req as never, schema);
      throw new Error("Expected RequestParser.body to throw");
    } catch (error) {
      expect(error).toBeInstanceOf(BadRequestError);
      expect((error as BadRequestError).message).toBe("Name is required");
      expect((error as BadRequestError).statusCode).toBe(400);
    }
  });

  test("throws BadRequestError when body cannot be parsed", async () => {
    const req = {
      json: async () => {
        throw new Error("Invalid JSON");
      },
    };

    try {
      await RequestParser.body(req as never, schema);
      throw new Error("Expected RequestParser.body to throw");
    } catch (error) {
      expect(error).toBeInstanceOf(BadRequestError);
      expect((error as BadRequestError).message).toBe("Failed to parse request body");
      expect((error as BadRequestError).statusCode).toBe(400);
    }
  });
});
