import { existsSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";

export const SERVER_ROOT = resolve(import.meta.dir, "..");
export const WORKSPACE_ROOT = resolve(SERVER_ROOT, "..");

export const PG_ROOT = join(WORKSPACE_ROOT, "tmp", "thesis_v4_6", "postgres");
export const PG_DATA = join(PG_ROOT, "data");
export const PG_LOG = join(PG_ROOT, "postgres.log");
export const PG_PORT = "55432";
export const PG_PASSWORD = "serlok_local_test_only";
export const PG_DATABASE = "serlok_test";
export const DATABASE_URL =
  `postgresql://postgres:${PG_PASSWORD}@127.0.0.1:${PG_PORT}/${PG_DATABASE}`;

const PG_PASSWORD_FILE = join(PG_ROOT, ".pgpass-init");

export const LOCAL_TEST_ENV = {
  BUN_ENV: "test",
  DATABASE_URL,
  JWT_SECRET: "local-test-secret-with-at-least-32-characters",
  PORT: "3001",
} as const;

export type SpawnResult = ReturnType<typeof Bun.spawnSync>;

export function postgresBin(name: string): string {
  const executable = process.platform === "win32" ? `${name}.exe` : name;
  const programFiles = process.env.ProgramFiles;
  const candidates = [
    programFiles ? join(programFiles, "PostgreSQL", "18", "bin", executable) : "",
    Bun.which(executable) ?? "",
  ].filter(Boolean);
  const found = candidates.find((candidate) => existsSync(candidate));
  if (!found) {
    throw new Error(`PostgreSQL executable not found: ${executable}`);
  }
  return found;
}

export function runCommand(
  command: string[],
  options: {
    cwd?: string;
    env?: Record<string, string>;
    quiet?: boolean;
    allowFailure?: boolean;
  } = {}
): SpawnResult {
  const result = Bun.spawnSync(command, {
    cwd: options.cwd ?? SERVER_ROOT,
    env: { ...process.env, ...options.env },
    stdout: options.quiet ? "pipe" : "inherit",
    stderr: options.quiet ? "pipe" : "inherit",
  });
  if (result.exitCode !== 0 && !options.allowFailure) {
    const stderr = options.quiet ? new TextDecoder().decode(result.stderr).trim() : "";
    throw new Error(
      `${command[0]} failed with exit code ${result.exitCode}${stderr ? `: ${stderr}` : ""}`
    );
  }
  return result;
}

export function ensureInsideWorkspace(path: string): void {
  const normalizedRoot = resolve(WORKSPACE_ROOT).toLowerCase().replaceAll("/", "\\");
  const normalizedPath = resolve(path).toLowerCase().replaceAll("/", "\\");
  if (!normalizedPath.startsWith(normalizedRoot)) {
    throw new Error(`Refusing to modify path outside workspace: ${path}`);
  }
}

function stdoutText(result: SpawnResult): string {
  return new TextDecoder().decode(result.stdout).trim();
}

export function queryPostgresVersion(): string {
  const psql = postgresBin("psql");
  const result = runCommand(
    [
      psql,
      "-h",
      "127.0.0.1",
      "-p",
      PG_PORT,
      "-U",
      "postgres",
      "-d",
      PG_DATABASE,
      "-tAc",
      "SELECT version();",
    ],
    { env: { PGPASSWORD: PG_PASSWORD }, quiet: true }
  );
  return stdoutText(result);
}

export interface LocalPostgres {
  databaseUrl: string;
  testEnv: Record<string, string>;
  startedByThisRun: boolean;
  stopIfStarted: () => void;
}

export function startLocalPostgres(
  options: { recreateDatabase?: boolean; migrate?: boolean } = {}
): LocalPostgres {
  const recreateDatabase = options.recreateDatabase ?? true;
  const migrate = options.migrate ?? true;

  mkdirSync(PG_ROOT, { recursive: true });

  const pgCtl = postgresBin("pg_ctl");
  const initdb = postgresBin("initdb");
  const createdb = postgresBin("createdb");
  const dropdb = postgresBin("dropdb");
  const pgIsReady = postgresBin("pg_isready");
  let startedByThisRun = false;

  if (!existsSync(join(PG_DATA, "PG_VERSION"))) {
    writeFileSync(PG_PASSWORD_FILE, `${PG_PASSWORD}\n`, { encoding: "utf8", mode: 0o600 });
    runCommand([
      initdb,
      "-D",
      PG_DATA,
      "-U",
      "postgres",
      `--pwfile=${PG_PASSWORD_FILE}`,
      "--auth-local=scram-sha-256",
      "--auth-host=scram-sha-256",
      "--encoding=UTF8",
      "--no-locale",
    ]);
    rmSync(PG_PASSWORD_FILE, { force: true });
  }

  const status = runCommand([pgCtl, "status", "-D", PG_DATA], {
    quiet: true,
    allowFailure: true,
  });
  if (status.exitCode !== 0) {
    runCommand([
      pgCtl,
      "start",
      "-D",
      PG_DATA,
      "-l",
      PG_LOG,
      "-w",
      "-o",
      `-p ${PG_PORT} -h 127.0.0.1`,
    ]);
    startedByThisRun = true;
  }

  runCommand([pgIsReady, "-h", "127.0.0.1", "-p", PG_PORT, "-U", "postgres", "-d", "postgres"]);
  const pgEnv = { PGPASSWORD: PG_PASSWORD };

  if (recreateDatabase) {
    runCommand(
      [dropdb, "--if-exists", "-h", "127.0.0.1", "-p", PG_PORT, "-U", "postgres", PG_DATABASE],
      { env: pgEnv }
    );
    runCommand(
      [createdb, "-h", "127.0.0.1", "-p", PG_PORT, "-U", "postgres", PG_DATABASE],
      { env: pgEnv }
    );
  }

  const testEnv = { ...LOCAL_TEST_ENV };

  if (migrate) {
    runCommand(["bun", "x", "--bun", "drizzle-kit", "migrate", "--config=drizzle.config.test.ts"], {
      env: testEnv,
    });
  }

  return {
    databaseUrl: DATABASE_URL,
    testEnv,
    startedByThisRun,
    stopIfStarted: () => {
      rmSync(PG_PASSWORD_FILE, { force: true });
      if (startedByThisRun) {
        runCommand([pgCtl, "stop", "-D", PG_DATA, "-m", "fast", "-w"], { allowFailure: true });
      }
    },
  };
}
