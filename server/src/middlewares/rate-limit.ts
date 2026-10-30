import type { Context, Next } from "hono";

type Bucket = { count: number; resetAt: number };

const buckets = new Map<string, Bucket>();

function clientKey(c: Context, scope: string): string {
  const forwarded = c.req.header("X-Forwarded-For")?.split(",")[0]?.trim();
  const address = forwarded || c.req.header("CF-Connecting-IP") || "unknown";
  return `${scope}:${address}`;
}

export function productionRateLimit(scope: string, max = 12, windowMs = 60_000) {
  return async (c: Context, next: Next) => {
    if (Bun.env.BUN_ENV !== "production") {
      await next();
      return;
    }

    const now = Date.now();
    const key = clientKey(c, scope);
    const bucket = buckets.get(key);
    if (!bucket || bucket.resetAt <= now) {
      buckets.set(key, { count: 1, resetAt: now + windowMs });
      await next();
      return;
    }
    if (bucket.count >= max) {
      c.header("Retry-After", String(Math.ceil((bucket.resetAt - now) / 1000)));
      return c.json({ ok: false, data: null, error: "Too many requests" }, 429);
    }
    bucket.count += 1;
    await next();
  };
}

export function clearRateLimitBuckets() {
  buckets.clear();
}
