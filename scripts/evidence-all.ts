import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { hostname } from "node:os";
import { join } from "node:path";
import {
  SERVER_ROOT,
  WORKSPACE_ROOT,
  ensureInsideWorkspace,
  queryPostgresVersion,
  runCommand,
  startLocalPostgres,
} from "../server/scripts/local-postgres";
import {
  pickOptimalThreshold,
  writeResultsMarkdown,
  type SuiteSummary,
} from "./render-results";

const EVIDENCE_DIR = join(WORKSPACE_ROOT, "evidence");
const ANDROID_ROOT = join(WORKSPACE_ROOT, "android", "serlok");
const BUN_JUNIT = join(EVIDENCE_DIR, "bun-junit.xml");
const GRADLE_RESULTS = join(
  ANDROID_ROOT,
  "app",
  "build",
  "test-results",
  "testDebugUnitTest"
);

interface JunitCounts {
  files: number;
  tests: number;
  assertions: number;
  durationMs: number;
  failures: number;
}

interface ParsedSuite {
  name: string;
  file: string;
  tests: number;
  assertions: number;
  durationMs: number;
  failures: number;
}

function attr(block: string, name: string): string {
  return new RegExp(`\\b${name}="([^"]*)"`).exec(block)?.[1] ?? "";
}

function parseTestsuites(xml: string): ParsedSuite[] {
  return [...xml.matchAll(/<testsuite\b([^>]*)>/g)].map((match) => {
    const block = match[1] ?? "";
    return {
      name: attr(block, "name"),
      file: attr(block, "file"),
      tests: Number(attr(block, "tests") || 0),
      assertions: Number(attr(block, "assertions") || 0),
      durationMs: Math.round(Number(attr(block, "time") || 0) * 1000),
      failures: Number(attr(block, "failures") || 0) + Number(attr(block, "errors") || 0),
    };
  });
}

function bestSuite(suites: ParsedSuite[], predicate: (suite: ParsedSuite) => boolean): JunitCounts {
  const matched = suites.filter(predicate).sort((a, b) => b.durationMs - a.durationMs);
  const suite = matched[0];
  if (!suite) {
    return { files: 1, tests: 0, assertions: 0, durationMs: 0, failures: 0 };
  }
  return {
    files: 1,
    tests: suite.tests,
    assertions: suite.assertions || suite.tests,
    durationMs: suite.durationMs,
    failures: suite.failures,
  };
}

function parseJunitXml(xml: string, fileCount: number): JunitCounts {
  const testcases = xml.match(/<testcase\b/g)?.length ?? 0;
  const failures =
    (xml.match(/<failure\b/g)?.length ?? 0) + (xml.match(/<error\b/g)?.length ?? 0);
  const suites = parseTestsuites(xml);
  const richest = [...suites].sort((a, b) => b.durationMs - a.durationMs)[0];
  return {
    files: fileCount,
    tests: testcases,
    assertions: richest?.assertions || testcases,
    durationMs: richest?.durationMs ?? 0,
    failures,
  };
}

function gradleWrapper(): string {
  return process.platform === "win32"
    ? join(ANDROID_ROOT, "gradlew.bat")
    : join(ANDROID_ROOT, "gradlew");
}

function formatId(value: number, digits: number): string {
  return value.toFixed(digits).replace(".", ",");
}

ensureInsideWorkspace(EVIDENCE_DIR);
rmSync(EVIDENCE_DIR, { recursive: true, force: true });
mkdirSync(EVIDENCE_DIR, { recursive: true });

const postgres = startLocalPostgres({ recreateDatabase: true, migrate: true });
let bunExit = 1;
let gradleExit = 1;
let postgresVersion = "";

try {
  postgresVersion = queryPostgresVersion();
  const evidenceEnv = {
    ...postgres.testEnv,
    SERLOK_EVIDENCE: "1",
    SERLOK_EVIDENCE_DIR: EVIDENCE_DIR,
  };

  const bunResult = runCommand(
    [
      "bun",
      "test",
      "tests/evidence/gating-evidence.test.ts",
      "tests/evidence/latency-evidence.test.ts",
      "--max-concurrency",
      "1",
      "--reporter=junit",
      `--reporter-outfile=${BUN_JUNIT}`,
    ],
    { cwd: SERVER_ROOT, env: evidenceEnv, allowFailure: true }
  );
  bunExit = bunResult.exitCode ?? 1;

  const gradleResult = runCommand(
    [
      gradleWrapper(),
      "detectionBenchmark",
      `-Pserlok.evidence.dir=${EVIDENCE_DIR}`,
    ],
    { cwd: ANDROID_ROOT, env: evidenceEnv, allowFailure: true }
  );
  gradleExit = gradleResult.exitCode ?? 1;

  let gating: JunitCounts = { files: 1, tests: 0, assertions: 0, durationMs: 0, failures: bunExit === 0 ? 0 : 1 };
  let latency: JunitCounts = { files: 1, tests: 0, assertions: 0, durationMs: 0, failures: bunExit === 0 ? 0 : 1 };
  if (existsSync(BUN_JUNIT)) {
    const bunXml = readFileSync(BUN_JUNIT, "utf8");
    const bunSuites = parseTestsuites(bunXml);
    gating = bestSuite(
      bunSuites,
      (suite) => suite.name === "gating evidence" || suite.file.includes("gating-evidence")
    );
    latency = bestSuite(
      bunSuites,
      (suite) => suite.name === "latency evidence" || suite.file.includes("latency-evidence")
    );
  }

  let detection: JunitCounts = {
    files: 1,
    tests: 0,
    assertions: 0,
    durationMs: 0,
    failures: gradleExit === 0 ? 0 : 1,
  };
  if (existsSync(GRADLE_RESULTS)) {
    const xmlFiles = readdirSync(GRADLE_RESULTS).filter((name) => name.endsWith(".xml"));
    const xml = xmlFiles.map((name) => readFileSync(join(GRADLE_RESULTS, name), "utf8")).join("\n");
    detection = parseJunitXml(xml, xmlFiles.length || 1);
  }

  const optimalThreshold = pickOptimalThreshold(EVIDENCE_DIR);
  const suites = [
    { name: "detection", ...detection },
    { name: "gating", ...gating },
    { name: "latency", ...latency },
  ];
  const summary: SuiteSummary = {
    generatedAt: new Date().toISOString(),
    masterSeed: 20260826,
    environment: {
      host: hostname(),
      os: `${process.platform} ${process.arch}`,
      bunVersion: Bun.version,
      postgresVersion,
      emulator: "tidak dipakai",
      disclaimer:
        "Angka latency berasal dari lingkungan lokal, bukan SLA produksi.",
    },
    suites,
    totals: {
      tests: suites.reduce((sum, suite) => sum + suite.tests, 0),
      failures: suites.reduce((sum, suite) => sum + suite.failures, 0),
      assertions: suites.reduce((sum, suite) => sum + suite.assertions, 0),
      durationMs: suites.reduce((sum, suite) => sum + suite.durationMs, 0),
    },
    optimalThreshold,
  };

  writeFileSync(join(EVIDENCE_DIR, "suite_summary.json"), JSON.stringify(summary, null, 2) + "\n");
  writeResultsMarkdown(EVIDENCE_DIR, summary);

  const failed = summary.totals.failures;
  console.log("");
  console.log("Ringkasan evidence");
  console.log(`  tes dijalankan: ${summary.totals.tests}`);
  console.log(`  tes gagal: ${failed}`);
  if (optimalThreshold) {
    console.log(
      `  threshold terbaik: ${formatId(optimalThreshold.g, 1)} g ` +
      `(accuracy ${formatId(optimalThreshold.accuracy, 4)}, FPR ${formatId(optimalThreshold.fpr, 4)})`
    );
  } else {
    console.log("  threshold terbaik: belum tersedia");
  }
  console.log(`  artefak: ${join(EVIDENCE_DIR, "RESULTS.md")}`);
} finally {
  postgres.stopIfStarted();
}

process.exitCode = bunExit === 0 && gradleExit === 0 ? 0 : 1;
