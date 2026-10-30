import { z } from "zod";

export const RegisterSchema = z.object({
  name: z.string().min(1, "Name is required").max(50, "Name must be less than 50 characters"),
  email: z.email("Invalid email format"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

export const LoginSchema = z.object({
  email: z.email("Invalid email format"),
  password: z.string().min(1, "Password is required"),
});
