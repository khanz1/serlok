import type { AuthService } from "./auth.service";
import { RegisterSchema, LoginSchema } from "./auth.validation";
import { RequestParser } from "../../utils/parser";
import type { IAppContext, IAuthContext } from "../../app.interface";

export class AuthController {
  constructor(private readonly authService: AuthService) {
    this.register = this.register.bind(this);
    this.login = this.login.bind(this);
    this.logout = this.logout.bind(this);
  }

  async register(c: IAppContext) {
    const body = await RequestParser.body(c.req, RegisterSchema);
    const result = await this.authService.register({
      name: body.name,
      email: body.email,
      password: body.password,
    });

    return c.json(c.var.success(result), 201);
  }

  async login(c: IAppContext) {
    const body = await RequestParser.body(c.req, LoginSchema);
    const result = await this.authService.login({
      email: body.email,
      password: body.password,
    });

    return c.json(c.var.success(result));
  }

  async logout(c: IAuthContext) {
    await this.authService.logout();
    return c.json(c.var.success(null));
  }
}
