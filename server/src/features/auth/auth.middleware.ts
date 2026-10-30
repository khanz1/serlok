import type { Next } from "hono";
import { UnauthorizedError } from "../../utils/error";
import type { IAuthContext } from "../../app.interface";
import { AppEnum } from "../../app.enum";
import type { TokenUtils } from "./token.utils";

export class AuthMiddleware {
  constructor(private tokenUtils: TokenUtils) {}

  authenticate = async (c: IAuthContext, next: Next) => {
    const authHeader = c.req.header("Authorization");
    if (!authHeader) {
      throw new UnauthorizedError("Missing Authorization header");
    }

    const token = authHeader.replace(/^Bearer\s+/i, "");
    const user = await this.tokenUtils.validateToken(token);

    if (!user) {
      throw new UnauthorizedError("Invalid token");
    }

    c.set(AppEnum.REQ_USER, user);
    await next();
  };
}
