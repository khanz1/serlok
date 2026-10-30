import { env } from "../src/env";

const skip = Bun.env.SERLOK_SKIP_PORT_CLEAR === "1" || Bun.env.SERLOK_SKIP_PORT_CLEAR === "true";

type Listener = { pid: number; name: string };

function lineReferencesPort(line: string, port: number): boolean {
  return new RegExp(`:${port}(?:\\s|$)`).test(line);
}

function parseSsListeners(port: number): Listener[] {
  const result = Bun.spawnSync(["ss", "-tlnp"], {
    stdout: "pipe",
    stderr: "pipe",
  });

  if (result.exitCode !== 0) {
    const err = new TextDecoder().decode(result.stderr);
    console.warn(
      `clear-port: "ss" failed (${result.exitCode}); skipping port check. ${err.trim()}`
    );
    return [];
  }

  const text = new TextDecoder().decode(result.stdout);
  const listeners: Listener[] = [];

  for (const line of text.split("\n")) {
    if (!line.includes("LISTEN")) continue;
    if (!lineReferencesPort(line, port)) continue;

    for (const m of line.matchAll(/\("([^"]+)",pid=(\d+)/g)) {
      listeners.push({ name: m[1]!, pid: Number(m[2]) });
    }
  }

  const byPid = new Map<number, string>();
  for (const l of listeners) {
    byPid.set(l.pid, l.name);
  }

  return [...byPid.entries()].map(([pid, name]) => ({ pid, name }));
}

async function readComm(pid: number): Promise<string | undefined> {
  try {
    const text = await Bun.file(`/proc/${pid}/comm`).text();
    return text.trim();
  } catch {
    return undefined;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function clearPort(): Promise<void> {
  if (skip) return;

  const port = env.PORT;
  let listeners = parseSsListeners(port);
  if (listeners.length === 0) return;

  for (const { pid, name } of listeners) {
    const comm = (await readComm(pid)) ?? name;

    if (comm !== "bun") {
      throw new Error(
        `clear-port: port ${port} is in use by "${comm}" (pid=${pid}), not bun. Stop that process or set a different PORT.`
      );
    }

    Bun.spawnSync(["kill", "-TERM", String(pid)], {
      stdout: "ignore",
      stderr: "ignore",
    });
  }

  const deadline = Date.now() + 5000;
  while (Date.now() < deadline) {
    listeners = parseSsListeners(port);
    if (listeners.length === 0) return;
    await sleep(100);
  }

  throw new Error(
    `clear-port: port ${port} still in use after SIGTERM. Pids: ${listeners.map((l) => l.pid).join(", ")}`
  );
}

await clearPort();
