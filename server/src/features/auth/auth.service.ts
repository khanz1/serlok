import { BadRequestError } from "../../utils/error";
import type { AuthResult, IServiceRegisterParams, IServiceLoginParams } from "./auth.type";
import { TokenUtils } from "./token.utils";
import { AuthRepository } from "./auth.repository";

export class AuthService {
  constructor(
    private readonly tokenUtils: TokenUtils,
    private readonly authRepository: AuthRepository
  ) {}

  async register(params: IServiceRegisterParams): Promise<AuthResult> {
    const user = await this.authRepository.findByEmail(params.email);
    if (user) {
      throw new BadRequestError("Email already registered");
    }

    const createdUser = await this.authRepository.create({
      name: params.name,
      email: params.email.toLowerCase(),
      passwordHash: await Bun.password.hash(params.password),
    });
    return this.tokenUtils.issueToken(this.authRepository.toPublicUser(createdUser));
  }

  async login(params: IServiceLoginParams): Promise<AuthResult> {
    const user = await this.authRepository.findByEmail(params.email);
    if (!user) {
      throw new BadRequestError("Invalid email or password");
    }

    const isPasswordValid = await Bun.password.verify(params.password, user.passwordHash);
    if (!isPasswordValid) {
      throw new BadRequestError("Invalid email or password");
    }

    return this.tokenUtils.issueToken(this.authRepository.toPublicUser(user));
  }

  async logout(): Promise<void> {}
}
