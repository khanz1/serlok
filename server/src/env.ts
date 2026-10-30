import { z } from "zod";

// Env files are loaded by Bun's --env-file CLI flag — no manual loading needed.

const envSchema = z.object({
  DATABASE_URL: z.string().min(1, "DATABASE_URL is required"),
  SUPABASE_PROJECT_REF: z
    .string()
    .regex(/^[a-z0-9]+$/)
    .optional(),
  JWT_SECRET: z.string().min(32, "JWT_SECRET must be at least 32 characters"),
  PORT: z.coerce.number().default(3000),
  CORS_ORIGINS: z.string().default(Bun.env.BUN_ENV === "production" ? "" : "*"),
});

const parsed = envSchema.safeParse(Bun.env);
if (!parsed.success) {
  const messages: string[] = [];
  for (const issue of parsed.error.issues) {
    messages.push(`${issue.path.join(".")}: ${issue.message}`);
  }
  throw new Error(`Invalid environment variables:\n${messages.join("\n")}`);
}

const validatedEnv = parsed.data;

function resolveDatabaseUrl(databaseUrl: string): string {
  if (!validatedEnv.SUPABASE_PROJECT_REF) {
    return databaseUrl;
  }

  const url = new URL(databaseUrl);
  url.username = `postgres.${validatedEnv.SUPABASE_PROJECT_REF}`;
  if (Bun.env.DB_PASSWORD) {
    url.password = Bun.env.DB_PASSWORD;
  }
  return url.toString();
}

export const env = {
  ...validatedEnv,
  DATABASE_URL: resolveDatabaseUrl(validatedEnv.DATABASE_URL),
};
