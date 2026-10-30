import { client } from "../src/db";
import { applyMigrations } from "../src/db/migrate";

await applyMigrations(client);
await client.end();
