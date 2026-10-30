import { existsSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { formatReport, mappingComplete, writeEvidenceReport } from "./evidence-report";
import {
  WORKSPACE_ROOT,
  ensureInsideWorkspace,
  runCommand,
  startLocalPostgres,
} from "./local-postgres";

const EVIDENCE_ROOT = join(WORKSPACE_ROOT, "tmp", "thesis_v4_6", "server-test");

const TEST_FILES = [
  "tests/auth/unit/auth.validation.test.ts",
  "tests/auth/unit/token.utils.test.ts",
  "tests/auth/e2e/logout.test.ts",
  "tests/auth/e2e/register.test.ts",
  "tests/auth/e2e/login.test.ts",
  "tests/health/unit/scope-routes.test.ts",
  "tests/health/unit/version.test.ts",
  "tests/health/e2e/health.test.ts",
  "tests/sos/integration/sos-repository.test.ts",
  "tests/user/e2e/user.test.ts",
  "tests/user/integration/user-service.test.ts",
  "tests/utils/unit/api-response.test.ts",
  "tests/utils/unit/error.test.ts",
  "tests/utils/unit/parser.test.ts",
  "tests/utils/unit/log.test.ts",
  "tests/ws/e2e/connection.test.ts",
  "tests/ws/integration/room-service.test.ts",
  "tests/ws/integration/sos-controller.test.ts",
  "tests/ws/unit/room.controller.ws.test.ts",
  "tests/ws/unit/room.ws.test.ts",
  "tests/ws/unit/sos.ws.test.ts",
  "tests/ws/unit/sos.controller.ws.test.ts",
  "tests/ws/unit/throttle.test.ts",
  "tests/ws/unit/ws.handler.test.ts",
  "tests/ws/unit/ws.manager.test.ts",
  "tests/ws/unit/ws.utils.test.ts",
  "tests/sos/unit/sos-controller-policy.test.ts",
] as const;

mkdirSync(EVIDENCE_ROOT, { recursive: true });
ensureInsideWorkspace(EVIDENCE_ROOT);
rmSync(EVIDENCE_ROOT, { recursive: true, force: true });
mkdirSync(join(EVIDENCE_ROOT, "coverage"), { recursive: true });

const postgres = startLocalPostgres({ recreateDatabase: true, migrate: true });

try {
  const startedAt = new Date().toISOString();
  const junitPath = join(EVIDENCE_ROOT, "junit.xml");
  const testCommand = [
    "bun",
    "test",
    "--coverage",
    "--coverage-skip-test-files",
    "--coverage-reporter=lcov",
    `--coverage-dir=${join(EVIDENCE_ROOT, "coverage")}`,
    "--reporter=junit",
    `--reporter-outfile=${junitPath}`,
    ...TEST_FILES,
  ];
  const testResult = runCommand(testCommand, {
    env: postgres.testEnv,
    allowFailure: true,
  });
  const lcovPath = join(EVIDENCE_ROOT, "coverage", "lcov.info");
  writeFileSync(
    join(EVIDENCE_ROOT, "run.json"),
    JSON.stringify(
      {
        environment: "test",
        startedAt,
        completedAt: new Date().toISOString(),
        command: "bun run test:evidence",
        files: TEST_FILES,
        exitCode: testResult.exitCode,
        junit: junitPath,
        lcov: lcovPath,
      },
      null,
      2
    )
  );

  let mappingFailed = false;
  if (existsSync(junitPath)) {
    const mapping = writeEvidenceReport({
      evidenceRoot: EVIDENCE_ROOT,
      junitPath,
      lcovPath,
      environment: "test",
      command: "bun run test:evidence",
      startedAt,
      exitCode: testResult.exitCode ?? 1,
      files: TEST_FILES.length,
    });
    console.log("");
    console.log(formatReport(mapping, true));
    mappingFailed = !mappingComplete(mapping);
  }

  process.exitCode = testResult.exitCode === 0 && !mappingFailed ? 0 : testResult.exitCode || 1;
} finally {
  postgres.stopIfStarted();
}
