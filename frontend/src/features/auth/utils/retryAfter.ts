import type { AxiosError } from "axios";

export function getRetryAfterSeconds(error: unknown): number | null {
  const retryAfter = (error as AxiosError).response?.headers?.["retry-after"];
  if (!retryAfter) return null;

  const parsed = Number.parseInt(String(retryAfter), 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}
