import { Hono } from "hono";
import type { Variables } from "hono/types";
import { UserRepository } from "./user.repository";
import { UserService } from "./user.service";
import { UserController } from "./user.controller";
import { authMiddleware } from "../auth/auth.module";

const userRepository = new UserRepository();
const userService = new UserService(userRepository);
const userController = new UserController(userService);

const userApp = new Hono<{ Variables: Variables }>();

userApp.get("/me", authMiddleware.authenticate, userController.me);

export default userApp;
export { userService };
