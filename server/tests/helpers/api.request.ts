import type { UserSeedModel } from "../../scripts/seeders/user.seed";
import app from "../../src/app";
import { UserSeeder } from "../../scripts/seeders/user.seed";
import type { ApiSuccessResponse } from "../../src/utils/api-response";
import type { AuthResult } from "../../src/features/auth/auth.type";

const userSeeder = new UserSeeder();

export class ApiRequest {
  private config: Headers;
  private request: typeof app.request;

  constructor() {
    this.config = new Headers();
    this.request = app.request;
  }

  setHeader(key: string, value: string): this {
    this.config.set(key, value);
    return this;
  }

  get(path: string, additionalHeaders?: NonNullable<RequestInit["headers"]>) {
    return this.request(`http://localhost${path}`, {
      method: "GET",
      headers: this.getHeaders(additionalHeaders),
    });
  }

  post(path: string, body: unknown, additionalHeaders?: NonNullable<RequestInit["headers"]>) {
    return this.request(`http://localhost${path}`, {
      method: "POST",
      headers: this.getHeaders(additionalHeaders),
      body: JSON.stringify(body),
    });
  }

  put(path: string, body: unknown, additionalHeaders?: NonNullable<RequestInit["headers"]>) {
    return this.request(`http://localhost${path}`, {
      method: "PUT",
      headers: this.getHeaders(additionalHeaders),
      body: JSON.stringify(body),
    });
  }

  patch(path: string, body: unknown, additionalHeaders?: NonNullable<RequestInit["headers"]>) {
    return this.request(`http://localhost${path}`, {
      method: "PATCH",
      headers: this.getHeaders(additionalHeaders),
      body: JSON.stringify(body),
    });
  }

  delete(path: string, additionalHeaders?: NonNullable<RequestInit["headers"]>) {
    return this.request(`http://localhost${path}`, {
      method: "DELETE",
      headers: this.getHeaders(additionalHeaders),
    });
  }

  deleteJson(path: string, body: unknown, additionalHeaders?: NonNullable<RequestInit["headers"]>) {
    return this.request(`http://localhost${path}`, {
      method: "DELETE",
      headers: this.getHeaders(additionalHeaders),
      body: JSON.stringify(body),
    });
  }

  private getHeaders(additionalHeaders?: NonNullable<RequestInit["headers"]>) {
    const headers = new Headers(this.config);
    headers.set("Content-Type", "application/json");
    if (additionalHeaders) {
      new Headers(additionalHeaders).forEach((value, key) => {
        headers.set(key, value);
      });
    }

    return headers;
  }

  async login(data: UserSeedModel) {
    await userSeeder.single(data);

    const res = await this.post("/api/auth/login", {
      email: data.email,
      password: data.password,
    });

    const body = (await res.json()) as ApiSuccessResponse<AuthResult>;
    return body.data;
  }
}
