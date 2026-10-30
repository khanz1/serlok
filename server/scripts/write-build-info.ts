import { writeFileSync } from "node:fs";
import { join, resolve } from "node:path";

const serverRoot = join(import.meta.dir, "..");
const repoRoot = join(serverRoot, "..");
const outputPath = resolve(Bun.argv[2] ?? join(serverRoot, "build-info.json"));

const result = Bun.spawnSync({
  cmd: ["git", "log", "-1", "--pretty=format:%h %s (%cs)"],
  cwd: repoRoot,
});

if (result.exitCode !== 0) {
  console.error(result.stderr.toString().trim() || "git log failed");
  process.exit(1);
}

const lastChanges = result.stdout.toString().trim();
if (!lastChanges) {
  console.error("git log returned empty last changes");
  process.exit(1);
}

writeFileSync(outputPath, `${JSON.stringify({ lastChanges }, null, 2)}\n`);
console.log(`Wrote lastChanges to ${outputPath}`);
