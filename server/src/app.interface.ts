import type { Context } from "hono";
import type { IPublicUser } from "./features/auth/auth.type";
import type { ApiResponse } from "./utils/api-response";

export interface IServiceOk<T extends Record<string, unknown>> {
  ok: true;
  data: T;
}

export interface IServiceError {
  ok: false;
  error: string;
}

export type IServiceResult<T extends Record<string, unknown>> = IServiceOk<T> | IServiceError;

export type IAppContext = Context<{
  Variables: {
    deviceId: string;
    deviceFingerprint: string;
    success: typeof ApiResponse.success;
    fail: typeof ApiResponse.fail;
  };
}>;

export type IAuthContext = Context<{
  Variables: {
    user: IPublicUser;
    deviceId: string;
    deviceFingerprint: string;
    success: typeof ApiResponse.success;
    fail: typeof ApiResponse.fail;
  };
}>;

export interface ILoginServiceResult {
  token: string;
  user: IPublicUser;
}
