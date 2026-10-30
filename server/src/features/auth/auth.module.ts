import { Hono } from "hono";
import type { Variables } from "hono/types";
import { AuthController } from "./auth.controller";
import { AuthService } from "./auth.service";
import { AuthMiddleware } from "./auth.middleware";
import { TokenUtils } from "./token.utils";
import { AuthRepository } from "./auth.repository";
import { productionRateLimit } from "../../middlewares/rate-limit";

export const authRepository = new AuthRepository();
export const tokenUtils = new TokenUtils(authRepository);
const authService = new AuthService(tokenUtils, authRepository);
export const authMiddleware = new AuthMiddleware(tokenUtils);
const authController = new AuthController(authService);

const authApp = new Hono<{ Variables: Variables }>();

authApp.post("/logout", authMiddleware.authenticate, authController.logout);

authApp.post("/register", productionRateLimit("auth-register"), authController.register);
authApp.post("/login", productionRateLimit("auth-login"), authController.login);

export default authApp;
export { authService };
