import { Hono } from "hono";
import { cors } from "hono/cors";
import { logger } from "hono/logger";
import { prettyJSON } from "hono/pretty-json";
import { AppError } from "./utils/error";

import healthController, { assertDatabaseReady } from "./features/health/health.controller";
import authRouter from "./features/auth/auth.module";
import userRouter from "./features/user/user.module";
import { ApiResponse } from "./utils/api-response";

export function createApp() {
  const app = new Hono<{
    Variables: {
      deviceId: string;
      deviceFingerprint: string;
      success: typeof ApiResponse.success;
      fail: typeof ApiResponse.fail;
    };
  }>();

  app.use("*", async (c, n) => {
    c.set("success", ApiResponse.success);
    c.set("fail", ApiResponse.fail);
    await n();
  });
  const corsOrigins = (Bun.env.CORS_ORIGINS ?? (Bun.env.BUN_ENV === "production" ? "" : "*"))
    .split(",")
    .map((origin) => origin.trim())
    .filter(Boolean);
  app.use(
    "*",
    cors({
      origin: (origin) => {
        if (!origin) return origin;
        if (corsOrigins.includes("*") || corsOrigins.includes(origin)) return origin;
        return "";
      },
    })
  );
  app.use("*", logger());
  app.use("*", prettyJSON());

  app.route("/api/health", healthController);
  app.get("/api/ready", async (c) => {
    try {
      await assertDatabaseReady();
      return c.json({ status: "ready", timestamp: new Date().toISOString() });
    } catch {
      return c.json({ status: "not_ready", timestamp: new Date().toISOString() }, 503);
    }
  });
  app.route("/api/auth", authRouter);
  app.route("/api/users", userRouter);

  app.onError((err, c) => {
    if (err instanceof AppError) {
      return c.json(c.var.fail(err.message), err.statusCode as any);
    }
    console.error(`❌ ${c.req.method} ${c.req.url} —`, err);
    return c.json(c.var.fail("Internal server error"), 500);
  });

  return app;
}

const app = createApp();
export default app;
