import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

export function evidenceDir(): string | null {
  const dir = Bun.env.SERLOK_EVIDENCE_DIR;
  if (!dir) return null;
  return dir;
}

export function csvField(value: string | number): string {
  const text = String(value);
  if (/[",\n]/.test(text)) {
    return `"${text.replaceAll('"', '""')}"`;
  }
  return text;
}

export function writeCsv(fileName: string, header: string[], rows: string[][]): void {
  const dir = evidenceDir();
  if (!dir) return;
  mkdirSync(dir, { recursive: true });
  const lines = [
    header.map(csvField).join(","),
    ...rows.map((row) => row.map(csvField).join(",")),
  ];
  writeFileSync(join(dir, fileName), `${lines.join("\n")}\n`, "utf8");
}
