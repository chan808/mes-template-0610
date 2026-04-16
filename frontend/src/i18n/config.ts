export const locales = ["ko", "en"] as const;

const configuredDefaultLocale = process.env.NEXT_PUBLIC_DEFAULT_LOCALE ?? "ko";

export const defaultLocale =
  locales.find((locale) => locale === configuredDefaultLocale) ?? "ko";
