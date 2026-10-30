function isAbsolutePath(value: string): boolean {
  return value.startsWith("/") || /^[A-Za-z]:[/\\]/.test(value);
}

/**
 * Resolve path segments without loading a compatibility filesystem module.
 * The first segment must be absolute, which keeps every caller anchored to an
 * explicit application or test directory.
 */
export function resolvePath(base: string, ...segments: string[]): string {
  if (!isAbsolutePath(base)) {
    throw new Error("Path base must be absolute");
  }

  const normalized = [base, ...segments].join("/").replaceAll("\\", "/");
  const drive = normalized.match(/^([A-Za-z]:)\//)?.[1];
  const body = drive ? normalized.slice(drive.length + 1) : normalized.slice(1);
  const resolved: string[] = [];

  for (const segment of body.split("/")) {
    if (!segment || segment === ".") continue;
    if (segment === "..") {
      resolved.pop();
      continue;
    }
    resolved.push(segment);
  }

  return drive ? `${drive}/${resolved.join("/")}` : `/${resolved.join("/")}`;
}
