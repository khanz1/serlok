import { NotFoundError } from "../../utils/error";
import type { IPublicUser } from "../auth/auth.type";
import type { UserRepository } from "./user.repository";

export class UserService {
  constructor(private readonly userRepository: UserRepository) {}

  async getMe(userId: string): Promise<IPublicUser> {
    const row = await this.userRepository.findById(userId);
    if (!row) {
      throw new NotFoundError("User not found");
    }
    return {
      id: row.id,
      name: row.name,
      email: row.email,
      createdAt: row.createdAt,
    };
  }
}
