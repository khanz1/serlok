export class AppError extends Error {
  statusCode: number;
  constructor(message: string, statusCode: number) {
    super(message);
    this.statusCode = statusCode;
  }
}

export class BadRequestError extends AppError {
  constructor(message: string) {
    super(message, 400);
  }
}

export class UnauthorizedError extends AppError {
  constructor(message: string) {
    super(message, 401);
  }
}

export class ForbiddenError extends AppError {
  constructor(message: string) {
    super(message, 403);
  }
}

export class NotFoundError extends AppError {
  constructor(message: string) {
    super(message, 404);
  }
}

export class InternalServerError extends AppError {
  constructor(message: string) {
    super(message, 500);
  }
}

/** Surfaces Postgres/Drizzle nested causes in server logs. */
export function formatDbError(err: unknown): string {
  const parts: string[] = [];
  let current: unknown = err;
  let depth = 0;
  while (current instanceof Error && depth < 4) {
    parts.push(current.message);
    current = (current as Error & { cause?: unknown }).cause;
    depth++;
  }
  if (parts.length === 0) return String(err);
  return parts.join(" | cause: ");
}
