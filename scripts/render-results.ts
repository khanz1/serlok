import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

export interface SuiteSummary {
  generatedAt: string;
  masterSeed: number;
  environment: {
    host: string;
    os: string;
    bunVersion: string;
    postgresVersion: string;
    emulator: string;
    disclaimer: string;
  };
  suites: Array<{
    name: string;
    files: number;
    tests: number;
    assertions: number;
    durationMs: number;
    failures: number;
  }>;
  totals: { tests: number; failures: number; assertions: number; durationMs: number };
  optimalThreshold: { g: number; accuracy: number; fpr: number } | null;
}

const SCENARIO_LABELS: Record<string, string> = {
  S1: "jatuh dari ketinggian pinggang ke permukaan keras",
  S2: "pengguna terjatuh dari sepeda motor (benturan tunggal keras)",
  S3: "terjatuh lalu terguling (rangkaian benturan 3 detik)",
  S4: "perangkat direbut kasar dari tangan",
  S5: "berlari tiba-tiba 10 detik",
  S6: "jalan cepat plus naik atau turun tangga",
  S7: "perangkat dimasukkan atau dikeluarkan dari saku",
  S8: "perangkat di dasbor motor melewati jalan bergelombang",
};

function parseCsv(text: string): { header: string[]; rows: string[][] } {
  const rows: string[][] = [];
  let current: string[] = [];
  let field = "";
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    const next = text[i + 1];
    if (inQuotes) {
      if (char === '"' && next === '"') {
        field += '"';
        i += 1;
      } else if (char === '"') {
        inQuotes = false;
      } else {
        field += char;
      }
      continue;
    }
    if (char === '"') {
      inQuotes = true;
      continue;
    }
    if (char === ",") {
      current.push(field);
      field = "";
      continue;
    }
    if (char === "\n") {
      current.push(field);
      if (current.some((cell) => cell.length > 0) || field.length > 0) {
        rows.push(current);
      }
      current = [];
      field = "";
      continue;
    }
    if (char !== "\r") {
      field += char;
    }
  }
  if (field.length > 0 || current.length > 0) {
    current.push(field);
    rows.push(current);
  }
  const header = rows.shift() ?? [];
  return { header, rows };
}

function readCsv(dir: string, name: string): { header: string[]; rows: string[][] } | null {
  const path = join(dir, name);
  if (!existsSync(path)) return null;
  return parseCsv(readFileSync(path, "utf8"));
}

function formatId(raw: string, digits?: number): string {
  if (raw === "" || raw === undefined) return "—";
  if (raw === "true") return "ya";
  if (raw === "false") return "tidak";
  const asNumber = Number(raw);
  if (!Number.isFinite(asNumber)) return raw;
  if (digits === undefined) {
    if (Number.isInteger(asNumber)) return String(asNumber);
    return String(asNumber).replace(".", ",");
  }
  return asNumber.toFixed(digits).replace(".", ",");
}

function markdownTable(headers: string[], rows: string[][]): string {
  const escape = (cell: string) => cell.replaceAll("|", "\\|");
  const line = (cells: string[]) => `| ${cells.map(escape).join(" | ")} |`;
  const sep = `| ${headers.map(() => "---").join(" | ")} |`;
  return [line(headers), sep, ...rows.map(line)].join("\n");
}

function captionedTable(caption: string, headers: string[], rows: string[][]): string {
  return `**${caption}**\n\n${markdownTable(headers, rows)}`;
}

export function renderResultsMarkdown(evidenceDir: string, summary: SuiteSummary | null): string {
  const sections: string[] = [];
  sections.push("# Hasil pengujian kuantitatif Serlok");
  sections.push("");
  sections.push(
    "Dokumen ini dihasilkan dari eksekusi nyata `bun run evidence:all`. Angka tidak ditulis tangan. Profil percepatan bersifat sintetis pada 50 Hz, memakai GForceDetector produksi, seed `20260826`, dan N = 20 repetisi per skenario. Ini bukan rekaman IMU lapangan, bukan uji populasi, dan bukan klaim medical fall detection."
  );
  sections.push("");
  sections.push(
    "Lokasi anggota disimpan di memori proses server, bukan pada tabel PostgreSQL. Kolom `rows_affected` pada bukti gating dihitung dari `COUNT(*)` terhadap `sos_events`. HTTP 403 membership tidak dipakai pada jalur WebSocket. `sos_resolve` berada di luar kontrak pesan klien."
  );
  sections.push("");

  if (summary) {
    sections.push(
      captionedTable(
        "Tabel E.1 Ringkasan suite pengujian",
        ["Suite", "Berkas", "Tes", "Assertion", "Durasi (ms)", "Gagal"],
        summary.suites.map((suite) => [
          suite.name,
          String(suite.files),
          String(suite.tests),
          String(suite.assertions),
          formatId(String(suite.durationMs), 0),
          String(suite.failures),
        ])
      )
    );
    sections.push("");
    sections.push(
      `Lingkungan latency: host ${summary.environment.host}, OS ${summary.environment.os}, Bun ${summary.environment.bunVersion}, PostgreSQL ${summary.environment.postgresVersion}, emulator ${summary.environment.emulator}. ${summary.environment.disclaimer}`
    );
    sections.push("");
  }

  const raw = readCsv(evidenceDir, "detection_raw.csv");
  if (raw) {
    sections.push(
      captionedTable(
        "Tabel E.2 Hasil mentah deteksi per repetisi (peak dalam g, first_trigger dalam ms)",
        [
          "Skenario",
          "Threshold (g)",
          "Seed",
          "Peak (g)",
          "Jumlah trigger",
          "Trigger pertama (ms)",
          "Re-arm",
          "Cooldown menahan",
        ],
        raw.rows.map((row) => [
          row[0] ?? "",
          formatId(row[1] ?? "", 1),
          row[2] ?? "",
          formatId(row[3] ?? "", 6),
          row[4] ?? "",
          row[5] ? formatId(row[5], 0) : "—",
          formatId(row[6] ?? ""),
          formatId(row[7] ?? ""),
        ])
      )
    );
    sections.push("");
  }

  const summaryCsv = readCsv(evidenceDir, "detection_summary.csv");
  if (summaryCsv) {
    sections.push(
      captionedTable(
        "Tabel E.3 Agregasi deteksi per skenario dan threshold. Simpangan baku peak memakai rumus sampel (n−1). n = 20.",
        [
          "Skenario",
          "Uraian",
          "Threshold (g)",
          "n",
          "Peak mean (g)",
          "Peak sd (g)",
          "Peak min (g)",
          "Peak max (g)",
          "Trigger rate",
          "Mean incident count",
        ],
        summaryCsv.rows.map((row) => [
          row[0] ?? "",
          SCENARIO_LABELS[row[0] ?? ""] ?? "",
          formatId(row[1] ?? "", 1),
          row[2] ?? "",
          formatId(row[3] ?? "", 6),
          formatId(row[4] ?? "", 6),
          formatId(row[5] ?? "", 6),
          formatId(row[6] ?? "", 6),
          formatId(row[7] ?? "", 4),
          formatId(row[8] ?? "", 4),
        ])
      )
    );
    sections.push("");
  }

  const confusion = readCsv(evidenceDir, "confusion_matrix.csv");
  if (confusion) {
    sections.push(
      captionedTable(
        "Tabel E.4 Matriks konfusi GForceDetector. S1–S4 kelas positif. S5–S8 kelas negatif. Prediksi positif jika trigger_count > 0.",
        [
          "Threshold (g)",
          "TP",
          "FP",
          "TN",
          "FN",
          "Precision",
          "Recall",
          "Accuracy",
          "FPR",
        ],
        confusion.rows.map((row) => [
          formatId(row[0] ?? "", 1),
          row[1] ?? "",
          row[2] ?? "",
          row[3] ?? "",
          row[4] ?? "",
          formatId(row[5] ?? "", 4),
          formatId(row[6] ?? "", 4),
          formatId(row[7] ?? "", 4),
          formatId(row[8] ?? "", 4),
        ])
      )
    );
    sections.push("");
  }

  const sweep = readCsv(evidenceDir, "threshold_sweep.csv");
  if (sweep) {
    sections.push(
      captionedTable(
        "Tabel E.5 Sweep threshold 2,0 g sampai 5,0 g dengan langkah 0,1 g. Accuracy dan FPR dihitung pada 160 jejak (8 skenario × 20 repetisi).",
        ["Threshold (g)", "TP", "FP", "TN", "FN", "Accuracy", "FPR"],
        sweep.rows.map((row) => [
          formatId(row[0] ?? "", 1),
          row[1] ?? "",
          row[2] ?? "",
          row[3] ?? "",
          row[4] ?? "",
          formatId(row[5] ?? "", 4),
          formatId(row[6] ?? "", 4),
        ])
      )
    );
    sections.push("");
  }

  const gating = readCsv(evidenceDir, "gating_evidence.csv");
  if (gating) {
    sections.push(
      captionedTable(
        "Tabel E.6 Bukti gating dan keamanan. rows_affected berasal dari COUNT pada sos_events, bukan asumsi insert.",
        ["Aksi", "Expected", "Actual", "Kode HTTP atau WS", "Baris terpengaruh", "Verdict"],
        gating.rows.map((row) => [
          row[0] ?? "",
          row[1] ?? "",
          row[2] ?? "",
          row[3] === "" ? "—" : (row[3] ?? ""),
          row[4] ?? "",
          row[5] ?? "",
        ])
      )
    );
    sections.push("");
  }

  const latency = readCsv(evidenceDir, "latency.csv");
  if (latency) {
    const eventRows = latency.rows.filter((row) => !String(row[0] ?? "").endsWith("_aggregate"));
    const aggRows = latency.rows.filter((row) => String(row[0] ?? "").endsWith("_aggregate"));
    sections.push(
      captionedTable(
        "Tabel E.7 Latency per event. t_send dan t_received memakai performance.now() pada proses yang sama. Satuan ms.",
        ["Jenis", "Urutan", "t_send (ms)", "t_received (ms)", "delta (ms)"],
        eventRows.map((row) => [
          row[0] ?? "",
          row[1] ?? "",
          formatId(row[2] ?? "", 3),
          formatId(row[3] ?? "", 3),
          formatId(row[4] ?? "", 3),
        ])
      )
    );
    sections.push("");
    sections.push(
      captionedTable(
        "Tabel E.8 Agregasi latency location relay dan SOS alert. Angka lingkungan lokal, bukan SLA produksi.",
        ["Jenis", "n", "Mean (ms)", "Median (ms)", "p95 (ms)", "Min (ms)", "Max (ms)"],
        aggRows.map((row) => [
          row[0] ?? "",
          row[5] ?? "",
          formatId(row[6] ?? "", 3),
          formatId(row[7] ?? "", 3),
          formatId(row[8] ?? "", 3),
          formatId(row[9] ?? "", 3),
          formatId(row[10] ?? "", 3),
        ])
      )
    );
    sections.push("");
  }

  if (summary?.optimalThreshold) {
    sections.push(
      `Threshold dengan accuracy tertinggi, lalu FPR terendah pada sweep, adalah ${formatId(String(summary.optimalThreshold.g), 1)} g (accuracy ${formatId(String(summary.optimalThreshold.accuracy), 4)}, FPR ${formatId(String(summary.optimalThreshold.fpr), 4)}).`
    );
    sections.push("");
  }

  return `${sections.join("\n").trim()}\n`;
}

export function pickOptimalThreshold(
  evidenceDir: string
): { g: number; accuracy: number; fpr: number } | null {
  const sweep = readCsv(evidenceDir, "threshold_sweep.csv");
  if (!sweep || sweep.rows.length === 0) return null;
  const parsed = sweep.rows
    .map((row) => ({
      g: Number(row[0]),
      accuracy: Number(row[5]),
      fpr: Number(row[6]),
    }))
    .filter((row) => Number.isFinite(row.g) && Number.isFinite(row.accuracy));
  if (parsed.length === 0) return null;
  parsed.sort((a, b) => {
    if (b.accuracy !== a.accuracy) return b.accuracy - a.accuracy;
    const fprA = Number.isFinite(a.fpr) ? a.fpr : Number.POSITIVE_INFINITY;
    const fprB = Number.isFinite(b.fpr) ? b.fpr : Number.POSITIVE_INFINITY;
    if (fprA !== fprB) return fprA - fprB;
    return a.g - b.g;
  });
  const best = parsed[0];
  if (!best) return null;
  return {
    g: best.g,
    accuracy: best.accuracy,
    fpr: Number.isFinite(best.fpr) ? best.fpr : 0,
  };
}

export function writeResultsMarkdown(
  evidenceDir: string,
  summary: SuiteSummary | null
): string {
  const markdown = renderResultsMarkdown(evidenceDir, summary);
  writeFileSync(join(evidenceDir, "RESULTS.md"), markdown, "utf8");
  return markdown;
}
