import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

type PackageIdentity = {
  name: string;
  version: string;
};

type BuildInfo = {
  lastChanges?: string;
};

const serverRoot = join(import.meta.dir, "..");
const packageJson = JSON.parse(
  readFileSync(join(serverRoot, "package.json"), "utf8")
) as PackageIdentity;

export const APP_NAME = packageJson.name;
export const APP_VERSION = packageJson.version;

export function resolveLastChanges(): string {
  const fromEnv = Bun.env.BUILD_LAST_CHANGES?.trim();
  if (fromEnv) {
    return fromEnv;
  }

  const buildInfoPath = join(serverRoot, "build-info.json");
  if (existsSync(buildInfoPath)) {
    const buildInfo = JSON.parse(readFileSync(buildInfoPath, "utf8")) as BuildInfo;
    const fromFile = buildInfo.lastChanges?.trim();
    if (fromFile) {
      return fromFile;
    }
  }

  const fromGit = readGitLastChanges();
  if (fromGit) {
    return fromGit;
  }

  return "unknown";
}

function readGitLastChanges(): string | undefined {
  const repoCandidates = [join(serverRoot, ".."), serverRoot];

  for (const cwd of repoCandidates) {
    const result = Bun.spawnSync({
      cmd: ["git", "log", "-1", "--pretty=format:%h %s (%cs)"],
      cwd,
    });

    if (result.exitCode !== 0) {
      continue;
    }

    const text = result.stdout.toString().trim();
    if (text) {
      return text;
    }
  }

  return undefined;
}

export const LAST_CHANGES = resolveLastChanges();
