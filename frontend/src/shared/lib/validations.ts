import { z } from "zod";

/**
 * Keep frontend password validation aligned with backend request validation.
 * The backend allows Unicode passphrases and only enforces length bounds.
 */
export const passwordSchema = z
  .string()
  .min(8, "Password must be at least 8 characters long.")
  .max(128, "Password must be at most 128 characters long.");
