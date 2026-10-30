import type { IAuthContext } from "../../app.interface";
import { AppEnum } from "../../app.enum";
import type { UserService } from "./user.service";

export class UserController {
  constructor(private readonly userService: UserService) {
    this.me = this.me.bind(this);
  }

  async me(c: IAuthContext) {
    const user = c.get(AppEnum.REQ_USER);
    const result = await this.userService.getMe(user.id);
    return c.json(c.var.success(result));
  }
}
