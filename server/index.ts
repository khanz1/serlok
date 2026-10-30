import app from "./src/app";
import { wsHandler, type WsData } from "./src/features/ws/ws.module";
import { tokenUtils } from "./src/features/auth/auth.module";
import { deviceRepository } from "./src/features/device/device.repository";
import { env } from "./src/env";
import { APP_VERSION } from "./src/version";

const server = Bun.serve<WsData>({
  port: env.PORT,
  async fetch(req, server) {
    const url = new URL(req.url);

    if (url.pathname === "/ws") {
      const token = url.searchParams.get("token") || "";
      const deviceInstanceId = url.searchParams.get("deviceInstanceId") || "unknown";
      const manufacturer = url.searchParams.get("manufacturer") || "";
      const model = url.searchParams.get("model") || "";
      const androidVersion = url.searchParams.get("androidVersion") || "";

      const user = token ? await tokenUtils.validateToken(token) : undefined;
      if (!user) {
        return new Response("Unauthorized", { status: 401 });
      }

      try {
        await deviceRepository.upsert({
          userId: user.id,
          deviceInstanceId,
          manufacturer,
          model,
          androidVersion,
        });
      } catch (err) {
        console.error(
          `❌ WS device upsert failed user=${user.id} device=${deviceInstanceId} —`,
          err
        );
        return new Response("Device persistence failed", { status: 500 });
      }

      const upgraded = server.upgrade(req, {
        data: {
          userId: user.id,
          deviceInstanceId,
          name: user.name,
          email: user.email,
          manufacturer,
          model,
          androidVersion,
          connectedAt: Date.now(),
        },
      });

      if (upgraded) return undefined;
      return new Response("WebSocket upgrade failed", { status: 400 });
    }

    return app.fetch(req);
  },

  websocket: wsHandler,
});

console.log(`
╔══════════════════════════════════════════════════════╗
║                                                      ║
║   Serlok Server v${APP_VERSION}  port ${env.PORT}
║                                                      ║
║   About:       http://localhost:${env.PORT}/
║   Health:      http://localhost:${env.PORT}/api/health
║   WebSocket:   ws://localhost:${env.PORT}/ws
║                                                      ║
╚══════════════════════════════════════════════════════╝
`);
