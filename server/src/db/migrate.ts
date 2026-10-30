import { readdir } from "node:fs/promises";
import { join } from "node:path";
import type postgres from "postgres";

const MIGRATIONS_DIR = join(import.meta.dir, "../../sql/migrations");

export async function applyMigrations(sql: postgres.Sql): Promise<void> {
  await sql`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      id text PRIMARY KEY,
      applied_at timestamptz NOT NULL DEFAULT now()
    )
  `;

  const appliedRows = await sql<{ id: string }[]>`
    SELECT id FROM schema_migrations
  `;
  const applied = new Set(appliedRows.map((row) => row.id));

  const files = (await readdir(MIGRATIONS_DIR))
    .filter((name) => name.endsWith(".sql"))
    .sort();

  for (const file of files) {
    if (applied.has(file)) {
      continue;
    }

    const sqlText = await Bun.file(join(MIGRATIONS_DIR, file)).text();
    await sql.unsafe(sqlText);
    await sql`INSERT INTO schema_migrations (id) VALUES (${file})`;
  }
}
