import type { Context, Next } from "hono";
import { BadRequestError } from "../utils/error";
import { AppEnum } from "../app.enum";

export class AppMiddleware {
  requireDeviceInfo = async (c: Context, next: Next) => {
    const deviceId = c.req.header("X-Device-Id");
    if (!deviceId) {
      throw new BadRequestError("Missing device information headers");
    }

    const manufacturer = c.req.header("X-Device-Manufacturer");
    const model = c.req.header("X-Device-Model");

    if (!manufacturer || !model) {
      throw new BadRequestError("Missing device information headers");
    }

    const deviceFingerprint = `${manufacturer}-${model}`;

    c.set(AppEnum.REQ_DEVICE_ID, deviceId);
    c.set(AppEnum.REQ_DEVICE_FINGERPRINT, deviceFingerprint);
    await next();
  };
}

export const appMiddleware = new AppMiddleware();
