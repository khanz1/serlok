import { client } from "../src/db";

const PRODUCTION_PROJECT = "prxjfrdbkybvnzxrbvhn";
const isProduction = Bun.env.BUN_ENV === "production";

if (
  isProduction &&
  (Bun.env.SUPABASE_PROJECT_REF !== PRODUCTION_PROJECT ||
    Bun.env.CONFIRM_DATABASE_RESET !== PRODUCTION_PROJECT)
) {
  throw new Error(
    `Production reset requires SUPABASE_PROJECT_REF and CONFIRM_DATABASE_RESET to equal ${PRODUCTION_PROJECT}`
  );
}

await client.begin(async (sql) => {
  await sql`drop table if exists public.sos_events cascade`;
  await sql`drop table if exists public.room_members cascade`;
  await sql`drop table if exists public.rooms cascade`;
  await sql`drop table if exists public.devices cascade`;
  await sql`drop table if exists public.refresh_tokens cascade`;
  await sql`drop table if exists public.users cascade`;
  await sql`drop table if exists drizzle.__drizzle_migrations`;
});

console.log(
  `[PASS] Reset completed for ${isProduction ? "production" : (Bun.env.BUN_ENV ?? "development")}`
);
await client.end();
