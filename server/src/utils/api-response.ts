export type ApiSuccessResponse<T = unknown> = {
  ok: true;
  data: T;
};

export type ApiFailResponse = {
  ok: false;
  data: null;
  error: string;
};

export type ApiResponseJson<T = unknown> = ApiSuccessResponse<T> | ApiFailResponse;

export class ApiResponse<T = unknown> {
  ok: boolean;
  data: T | null;
  error?: string;

  protected constructor(ok: boolean, data: T | null, error?: string) {
    this.ok = ok;
    this.data = data;
    this.error = error;
  }

  static success<T>(data: T): ApiResponse<T> {
    return new ApiResponse(true, data);
  }

  static fail<T = unknown>(error: string): ApiResponse<T> {
    return new ApiResponse<T>(false, null, error);
  }

  map<U>(fn: (data: T) => U): ApiResponse<U> {
    if (!this.ok || this.data === null) {
      return ApiResponse.fail(this.error ?? "Unknown error");
    }

    return ApiResponse.success(fn(this.data));
  }

  toJSON(): ApiResponseJson<T> {
    if (this.ok) {
      return { ok: true, data: this.data as T };
    }

    return { ok: false, data: null, error: this.error ?? "Unknown error" };
  }

  isOk(): this is ApiResponse<T> & { data: T } {
    return this.ok;
  }
}
