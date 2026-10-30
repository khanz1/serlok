import type { Config } from "drizzle-kit";

// DATABASE_URL is loaded via --env-file flag from the script command.

export default {
  schema: "./src/db/schema.ts",
  out: "./drizzle",
  dialect: "postgresql",
  dbCredentials: { url: Bun.env.DATABASE_URL! },
} satisfies Config;
