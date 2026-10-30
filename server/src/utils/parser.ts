import { z } from "zod";
import { BadRequestError } from "./error";
import type { HonoRequest } from "hono";

export class RequestParser {
  static async body<T>(req: HonoRequest, schema: z.ZodSchema<T>): Promise<T> {
    try {
      const body = await req.json();
      const data = await schema.parseAsync(body);
      return data;
    } catch (error) {
      if (error instanceof z.ZodError) {
        throw new BadRequestError(error.issues[0]?.message ?? "Validation error");
      }
      throw new BadRequestError("Failed to parse request body");
    }
  }
}
