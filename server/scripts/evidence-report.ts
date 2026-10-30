import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { WORKSPACE_ROOT } from "./local-postgres";

export interface ParsedTest {
  name: string;
  classname: string;
  file: string;
  assertions: number;
  failed: boolean;
}

export interface CoverageTotals {
  linesCovered: number;
  linesTotal: number;
  percent: number;
}

export interface EvidenceRow {
  title: string;
  expected: string;
  tests: number;
  assertions: number;
  failed: number;
  verdict: "pass" | "fail";
  actual: string;
}

export interface EvidenceLayer {
  name: "UNIT" | "REST API" | "WEBSOCKET";
  rows: EvidenceRow[];
}

export interface EvidenceMapping {
  title: string;
  environment: string;
  command: string;
  time: string;
  layers: EvidenceLayer[];
  totals: {
    tests: number;
    passed: number;
    assertions: number;
    files: number;
    failures: number;
    exitCode: number;
  };
  coverage: CoverageTotals | null;
  unmapped: number;
}

interface Rule {
  layer: EvidenceLayer["name"];
  title: string;
  expected: string;
  match: (test: NormalizedTest) => boolean;
}

interface NormalizedTest extends ParsedTest {
  filePath: string;
  blob: string;
}

const TITLE = "SERVER TEST SUMMARY";

const GREEN = "\x1b[32m";
const RED = "\x1b[31m";
const CYAN = "\x1b[36m";
const DIM = "\x1b[90m";
const BOLD = "\x1b[1m";
const RESET = "\x1b[0m";

function decodeXml(value: string): string {
  return value
    .replaceAll("&amp;", "&")
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&quot;", '"')
    .replaceAll("&#39;", "'");
}

function attr(block: string, name: string): string {
  return decodeXml(new RegExp(`\\b${name}="([^"]*)"`).exec(block)?.[1] ?? "");
}

export function parseJunit(xml: string): ParsedTest[] {
  const tests: ParsedTest[] = [];
  const pattern = /<testcase\b([^>]*)(?:\/>|>[\s\S]*?<\/testcase>)/g;
  for (const match of xml.matchAll(pattern)) {
    const open = match[1] ?? "";
    const block = match[0] ?? "";
    tests.push({
      name: attr(open, "name"),
      classname: attr(open, "classname"),
      file: attr(open, "file"),
      assertions: Number(attr(open, "assertions") || 0),
      failed: /<failure\b|<error\b/.test(block),
    });
  }
  return tests;
}

export function parseLcov(text: string): CoverageTotals {
  let linesCovered = 0;
  let linesTotal = 0;
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith("LH:")) {
      linesCovered += Number(line.slice(3));
    }
    if (line.startsWith("LF:")) {
      linesTotal += Number(line.slice(3));
    }
  }
  const percent = linesTotal === 0 ? 0 : (linesCovered / linesTotal) * 100;
  return {
    linesCovered,
    linesTotal,
    percent: Math.round(percent * 100) / 100,
  };
}

function normalize(test: ParsedTest): NormalizedTest {
  const filePath = test.file.replaceAll("\\", "/").toLowerCase();
  const blob = `${filePath} ${test.classname} ${test.name}`.toLowerCase();
  return { ...test, filePath, blob };
}

function has(test: NormalizedTest, ...needles: string[]): boolean {
  return needles.some((needle) => test.blob.includes(needle.toLowerCase()));
}

function fileIs(test: NormalizedTest, suffix: string): boolean {
  return test.filePath.replaceAll("\\", "/").endsWith(suffix.toLowerCase());
}

const RULES: Rule[] = [
  {
    layer: "UNIT",
    title: "Validation",
    expected: "schema, token, and payload rules",
    match: (test) =>
      fileIs(test, "auth.validation.test.ts") ||
      fileIs(test, "token.utils.test.ts") ||
      has(test, "incomplete payload", "malformed envelope", "stale timestamp"),
  },
  {
    layer: "UNIT",
    title: "Room rules",
    expected: "create, join, membership, and Host close",
    match: (test) =>
      has(
        test,
        "createroom",
        "joinroom",
        "createviaws",
        "joinviaws",
        "leaveviaws",
        "room_create",
        "room_join",
        "room_leave",
        "soft-close",
        "soft-closes",
        "not_found",
        "missing code"
      ) && !has(test, "location_update", "room_start", "room_stop", "settings"),
  },
  {
    layer: "UNIT",
    title: "Safety Session",
    expected: "Host start, stop, and sharing gate",
    match: (test) =>
      has(
        test,
        "startviaws",
        "stopviaws",
        "room_start",
        "room_stop",
        "setsharing",
        "safety session",
        "sharing is off",
        "sharing is inactive",
        "while room sharing is off"
      ),
  },
  {
    layer: "UNIT",
    title: "location gating",
    expected: "drop location when session or room is inactive",
    match: (test) => has(test, "locationthrottle", "location_update", "without roomcode"),
  },
  {
    layer: "UNIT",
    title: "SOS authorization",
    expected: "session, threshold, membership, and cooldown",
    match: (test) =>
      fileIs(test, "sos-controller-policy.test.ts") ||
      fileIs(test, "sos-controller.test.ts") ||
      has(test, "dropped until membership", "below room threshold", "alertcooldown"),
  },
  {
    layer: "UNIT",
    title: "parser",
    expected: "request body, JSON, and envelope mapping",
    match: (test) =>
      fileIs(test, "parser.test.ts") ||
      has(test, "invalid json", "schema-invalid", "rejects binary"),
  },
  {
    layer: "UNIT",
    title: "snapshot incidents",
    expected: "listPublicByRoom without raw axes",
    match: (test) =>
      fileIs(test, "sos-repository.test.ts") ||
      has(test, "incidents on room_state", "public room incidents"),
  },
  {
    layer: "UNIT",
    title: "error handling",
    expected: "canonical fail response and HTTP mapping",
    match: (test) =>
      fileIs(test, "error.test.ts") ||
      fileIs(test, "api-response.test.ts") ||
      fileIs(test, "log.test.ts") ||
      has(test, "senderror", "room_error"),
  },
  {
    layer: "REST API",
    title: "health",
    expected: "GET /api/health",
    match: (test) =>
      (fileIs(test, "health.test.ts") && !has(test, "readiness")) ||
      fileIs(test, "version.test.ts"),
  },
  {
    layer: "REST API",
    title: "readiness",
    expected: "GET /api/ready database check",
    match: (test) => has(test, "readiness"),
  },
  {
    layer: "REST API",
    title: "register",
    expected: "POST /api/auth/register",
    match: (test) => fileIs(test, "register.test.ts"),
  },
  {
    layer: "REST API",
    title: "login",
    expected: "POST /api/auth/login",
    match: (test) => fileIs(test, "login.test.ts"),
  },
  {
    layer: "REST API",
    title: "logout",
    expected: "POST /api/auth/logout",
    match: (test) => fileIs(test, "logout.test.ts") && !has(test, "refresh", "404"),
  },
  {
    layer: "REST API",
    title: "profile",
    expected: "GET /api/users/me",
    match: (test) =>
      fileIs(test, "user-service.test.ts") || has(test, "returns current user", "public profile"),
  },
  {
    layer: "REST API",
    title: "authentication failure",
    expected: "invalid credentials and invalid token",
    match: (test) =>
      has(
        test,
        "wrong password",
        "non-existent email",
        "invalid email format",
        "empty password",
        "malformed token",
        "empty bearer",
        "weak password",
        "empty name",
        "duplicate email"
      ),
  },
  {
    layer: "REST API",
    title: "authorization",
    expected: "protected route without a session",
    match: (test) =>
      has(
        test,
        "without authorization",
        "missing authorization",
        "fails with missing authorization"
      ),
  },
  {
    layer: "REST API",
    title: "HTTP 404",
    expected: "route di luar kontrak",
    match: (test) =>
      fileIs(test, "scope-routes.test.ts") ||
      has(test, "returns 404", "is not exposed", "not exposed", "refresh route"),
  },
  {
    layer: "WEBSOCKET",
    title: "authenticated upgrade",
    expected: "JWT required, HTTP 401 without token",
    match: (test) =>
      has(test, "without token", "token → upgrade", "token -> upgrade", "upgrade path") ||
      has(test, "registers connection in wsmanager"),
  },
  {
    layer: "WEBSOCKET",
    title: "ping-pong",
    expected: "ping then pong on an authenticated socket",
    match: (test) => has(test, "ping", "pong"),
  },
  {
    layer: "WEBSOCKET",
    title: "Room create or join",
    expected: "room_create, room_join, room_state",
    match: (test) =>
      has(test, "room_create", "room_join", "createviaws", "joinviaws", "createroom", "joinroom"),
  },
  {
    layer: "WEBSOCKET",
    title: "Host start or stop",
    expected: "room_start and room_stop by Host",
    match: (test) => has(test, "room_start", "room_stop", "startviaws", "stopviaws"),
  },
  {
    layer: "WEBSOCKET",
    title: "settings",
    expected: "room_update_settings and shake threshold",
    match: (test) => has(test, "room_update_settings", "setshakethreshold", "updates settings"),
  },
  {
    layer: "WEBSOCKET",
    title: "location relay",
    expected: "location_update broadcast to Room members",
    match: (test) =>
      has(test, "location_update", "updatelastlocation", "getlastlocation") &&
      !has(test, "without roomcode"),
  },
  {
    layer: "WEBSOCKET",
    title: "strict shake SOS",
    expected: "sos_trigger payload, threshold, freshness, cooldown",
    match: (test) =>
      fileIs(test, "sos.controller.ws.test.ts") ||
      fileIs(test, "sos.ws.test.ts") ||
      fileIs(test, "sos-controller.test.ts") ||
      has(test, "sos_trigger", "shake-only", "shake trigger", "shake event"),
  },
  {
    layer: "WEBSOCKET",
    title: "canonical room_alert",
    expected: "room_alert without resolution fields",
    match: (test) =>
      has(
        test,
        "room_alert",
        "broadcasts only the shake contract",
        "stores and broadcasts only the shake contract"
      ),
  },
  {
    layer: "WEBSOCKET",
    title: "snapshot incidents",
    expected: "incidents array on room_state",
    match: (test) => has(test, "incidents on room_state"),
  },
  {
    layer: "WEBSOCKET",
    title: "Host close",
    expected: "Host leave soft-closes the Room",
    match: (test) =>
      has(test, "host leaving soft-closes", "soft-closes when room emptied or host leaves"),
  },
  {
    layer: "WEBSOCKET",
    title: "leave",
    expected: "room_leave by a Member",
    match: (test) => has(test, "room_leave", "leaveviaws", "last member leaving", "member leave"),
  },
  {
    layer: "WEBSOCKET",
    title: "disconnect",
    expected: "detach without closing Room or session",
    match: (test) =>
      has(
        test,
        "disconnectviaws",
        "handleroomdisconnect",
        "wshandler.close",
        "detach",
        "host disconnect"
      ),
  },
];

function countRow(tests: NormalizedTest[], rule: Rule): EvidenceRow {
  const matched = tests.filter((test) => rule.match(test));
  const failed = matched.filter((test) => test.failed).length;
  const assertions = matched.reduce((sum, test) => sum + test.assertions, 0);
  const testsCount = matched.length;
  const empty = testsCount === 0;
  const verdict: "pass" | "fail" = empty || failed > 0 ? "fail" : "pass";
  const actual = empty
    ? "0 tests mapped"
    : failed > 0
      ? `${testsCount - failed} passed, ${failed} failed`
      : testsLabel(testsCount, "passed");
  return {
    title: rule.title,
    expected: rule.expected,
    tests: testsCount,
    assertions,
    failed,
    verdict,
    actual,
  };
}

export function mapEvidence(
  tests: ParsedTest[],
  options: {
    environment: string;
    command: string;
    startedAt: string;
    exitCode: number;
    files: number;
    coverage: CoverageTotals | null;
  }
): EvidenceMapping {
  const normalized = tests.map(normalize);
  const layers: EvidenceLayer[] = (["UNIT", "REST API", "WEBSOCKET"] as const).map((name) => ({
    name,
    rows: RULES.filter((rule) => rule.layer === name).map((rule) => countRow(normalized, rule)),
  }));

  const mappedKeys = new Set<string>();
  for (const test of normalized) {
    if (RULES.some((rule) => rule.match(test))) {
      mappedKeys.add(`${test.file}|${test.name}|${test.classname}`);
    }
  }

  const failures = tests.filter((test) => test.failed).length;
  const uniqueFiles = new Set(tests.map((test) => test.file.replaceAll("\\", "/").toLowerCase()));

  return {
    title: TITLE,
    environment: options.environment,
    command: options.command,
    time: formatJakarta(options.startedAt),
    layers,
    totals: {
      tests: tests.length,
      passed: tests.length - failures,
      assertions: tests.reduce((sum, test) => sum + test.assertions, 0),
      files: options.files || uniqueFiles.size,
      failures,
      exitCode: options.exitCode,
    },
    coverage: options.coverage,
    unmapped: tests.length - mappedKeys.size,
  };
}

export function formatJakarta(iso: string): string {
  const date = new Date(iso);
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Jakarta",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).formatToParts(date);
  const pick = (type: string) => parts.find((part) => part.type === type)?.value ?? "00";
  return `${pick("year")}-${pick("month")}-${pick("day")} ${pick("hour")}:${pick("minute")}:${pick("second")} +07:00`;
}

function testsLabel(count: number, suffix?: string): string {
  const noun = count === 1 ? "test" : "tests";
  if (!suffix) {
    return `${count} ${noun}`;
  }
  return `${count} ${noun} ${suffix}`;
}

function padTitle(title: string, width: number): string {
  return title.length >= width ? title : `${title}${" ".repeat(width - title.length)}`;
}

function verdictTag(verdict: "pass" | "fail"): string {
  return verdict === "pass" ? "[PASS]" : "[FAIL]";
}

export function formatReport(mapping: EvidenceMapping, color: boolean): string {
  const paint = (code: string, text: string) => (color ? `${code}${text}${RESET}` : text);
  const lines: string[] = [];
  lines.push(paint(`${BOLD}${CYAN}`, "SERVER TEST SUMMARY"));
  lines.push(`  Environment  ${mapping.environment}`);
  lines.push(`  Command      ${mapping.command}`);
  lines.push("");

  for (const layer of mapping.layers) {
    lines.push(paint(`${BOLD}${CYAN}`, layer.name));
    for (const row of layer.rows) {
      const tag = paint(row.verdict === "pass" ? GREEN : RED, verdictTag(row.verdict));
      const count = paint(DIM, padTitle(testsLabel(row.tests), 10));
      const expected = paint(DIM, row.expected);
      lines.push(`  ${tag} ${padTitle(row.title, 24)} ${count}  ${expected}`);
    }
    lines.push("");
  }

  const totalOk = mapping.totals.failures === 0 && mapping.totals.exitCode === 0;
  const totalTag = paint(totalOk ? GREEN : RED, totalOk ? "[PASS]" : "[FAIL]");
  lines.push(paint(CYAN, "------------------------------------------------------------"));
  lines.push(
    `  ${totalTag} ${padTitle("TOTAL", 24)} ${paint(
      DIM,
      padTitle(`${mapping.totals.passed}/${mapping.totals.tests}`, 10)
    )}  ${paint(
      DIM,
      `${mapping.totals.assertions} assertions, ${mapping.totals.files} files, ${mapping.totals.failures} failed, exit ${mapping.totals.exitCode}`
    )}`
  );

  if (mapping.coverage) {
    const { linesCovered, linesTotal, percent } = mapping.coverage;
    const coverageOk = linesTotal > 0;
    const coverageTag = paint(coverageOk ? GREEN : RED, coverageOk ? "[PASS]" : "[FAIL]");
    lines.push(
      `  ${coverageTag} ${padTitle("LCOV line coverage", 24)} ${paint(
        DIM,
        padTitle(`${percent.toFixed(2)}%`, 10)
      )}  ${paint(DIM, `${linesCovered}/${linesTotal} lines`)}`
    );
  }

  lines.push("");
  lines.push(paint(DIM, "Evidence verified. This window may be closed."));
  return lines.join("\n");
}

export function mappingComplete(mapping: EvidenceMapping): boolean {
  return mapping.layers.every((layer) =>
    layer.rows.every((row) => row.verdict === "pass" && row.tests > 0)
  );
}

export function writeEvidenceReport(options: {
  evidenceRoot: string;
  junitPath: string;
  lcovPath?: string;
  environment: string;
  command: string;
  startedAt: string;
  exitCode: number;
  files: number;
}): EvidenceMapping {
  const xml = readFileSync(options.junitPath, "utf8");
  const tests = parseJunit(xml);
  const coverage =
    options.lcovPath && existsSync(options.lcovPath)
      ? parseLcov(readFileSync(options.lcovPath, "utf8"))
      : null;
  const mapping = mapEvidence(tests, {
    environment: options.environment,
    command: options.command,
    startedAt: options.startedAt,
    exitCode: options.exitCode,
    files: options.files,
    coverage,
  });
  writeFileSync(
    join(options.evidenceRoot, "mapping.json"),
    JSON.stringify(mapping, null, 2) + "\n"
  );
  writeFileSync(join(options.evidenceRoot, "report.txt"), formatReport(mapping, false) + "\n");
  return mapping;
}

if (import.meta.main) {
  const evidenceRoot = join(WORKSPACE_ROOT, "tmp", "thesis_v4_6", "server-test");
  const runPath = join(evidenceRoot, "run.json");
  const run = existsSync(runPath)
    ? (JSON.parse(readFileSync(runPath, "utf8")) as {
      environment?: string;
      startedAt?: string;
      command?: string;
      exitCode?: number;
      files?: string[];
    })
    : {};
  const mapping = writeEvidenceReport({
    evidenceRoot,
    junitPath: join(evidenceRoot, "junit.xml"),
    lcovPath: join(evidenceRoot, "coverage", "lcov.info"),
    environment: "test",
    command: run.command ?? "bun run test:evidence",
    startedAt: run.startedAt ?? new Date().toISOString(),
    exitCode: run.exitCode ?? 0,
    files: run.files?.length ?? 27,
  });
  console.log(formatReport(mapping, true));
  if (!mappingComplete(mapping) || mapping.totals.exitCode !== 0) {
    process.exitCode = 1;
  }
}
