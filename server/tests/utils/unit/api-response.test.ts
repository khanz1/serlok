import { describe, expect, test } from "bun:test";
import { ApiResponse } from "../../../src/utils/api-response";

describe("ApiResponse", () => {
  describe("success", () => {
    test("creates a success response without error property", () => {
      const response = ApiResponse.success({ id: "user-1" });
      const json = response.toJSON();

      expect(json.ok).toBe(true);
      expect(json.data).toEqual({ id: "user-1" });
      expect("error" in json).toBe(false);
    });

    test("supports null as successful data", () => {
      const response = ApiResponse.success(null);
      const json = response.toJSON();

      expect(json.ok).toBe(true);
      expect(json.data).toBeNull();
      expect("error" in json).toBe(false);
    });
  });

  describe("fail", () => {
    test("creates a failed response with error message", () => {
      const response = ApiResponse.fail("Something went wrong");
      const json = response.toJSON();

      expect(json.ok).toBe(false);
      if (!json.ok) {
        expect(json.data).toBeNull();
        expect(json.error).toBe("Something went wrong");
      }
    });
  });

  describe("map", () => {
    test("maps success data", () => {
      const response = ApiResponse.success(2).map((value) => value * 2);
      const json = response.toJSON();

      expect(json.ok).toBe(true);
      expect(json.data).toBe(4);
    });

    test("keeps failed response as failed", () => {
      const response = ApiResponse.fail<number>("Invalid data").map((value) => value * 2);
      const json = response.toJSON();

      expect(json.ok).toBe(false);
      if (!json.ok) {
        expect(json.data).toBeNull();
        expect(json.error).toBe("Invalid data");
      }
    });

    test("does not map null success data", () => {
      const response = ApiResponse.success<number | null>(null).map((value) => value ?? 1);
      const json = response.toJSON();

      expect(json.ok).toBe(false);
      if (!json.ok) {
        expect(json.data).toBeNull();
        expect(json.error).toBe("Unknown error");
      }
    });
  });

  describe("isOk", () => {
    test("returns true for success response", () => {
      expect(ApiResponse.success("ok").isOk()).toBe(true);
    });

    test("returns false for failed response", () => {
      expect(ApiResponse.fail("fail").isOk()).toBe(false);
    });
  });
});
