import { SignJWT, jwtVerify } from "jose";
import { env } from "../../env";
import type { IPublicUser, AuthResult } from "./auth.type";
import type { AuthRepository } from "./auth.repository";

export class TokenUtils {
  private readonly JWT_SECRET = new TextEncoder().encode(env.JWT_SECRET);
  private readonly ALGORITHM = "HS256";
  private readonly TOKEN_TTL = "1d";

  constructor(private readonly authRepository: AuthRepository) {}

  signToken(sub: string, email: string, name: string): Promise<string> {
    return new SignJWT({ sub, email, name })
      .setProtectedHeader({ alg: this.ALGORITHM })
      .setIssuedAt()
      .setExpirationTime(this.TOKEN_TTL)
      .sign(this.JWT_SECRET);
  }

  async validateToken(token: string): Promise<IPublicUser | undefined> {
    try {
      const { payload } = await jwtVerify(token, this.JWT_SECRET, {
        algorithms: [this.ALGORITHM],
      });
      const user = await this.authRepository.findById(payload.sub!);
      return user || undefined;
    } catch {
      return undefined;
    }
  }

  async issueToken(user: IPublicUser): Promise<AuthResult> {
    return {
      token: await this.signToken(user.id, user.email, user.name),
      user,
    };
  }
}
