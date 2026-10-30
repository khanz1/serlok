import type { Config } from "drizzle-kit";
import { env } from "./src/env";

// DATABASE_URL is loaded via --env-file flag from the script command.

export default {
  schema: "./src/db/schema.ts",
  out: "./drizzle",
  dialect: "postgresql",
  dbCredentials: { url: env.DATABASE_URL },
} satisfies Config;
