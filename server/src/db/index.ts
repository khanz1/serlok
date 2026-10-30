import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema";
import { env } from "../env";

const client = postgres(env.DATABASE_URL, {
  debug: Bun.env.BUN_ENV === "development",
});

export const db = drizzle(client, {
  schema,
  logger: Bun.env.BUN_ENV === "development",
});
export { client };
