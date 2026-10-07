// Browser-level preferences that apply before a user signs in (for example the UI language).
import { storage } from "@/storage/local-storage";
import { KEYS } from "@/storage/keys";

export type Locale = "en" | "ko";
export const LOCALES: Locale[] = ["en", "ko"];

export type SiteTheme = "light" | "dark";

interface Preferences {
  locale?: Locale;
  /** Theme for pages shown before sign-in (landing, login). */
  siteTheme?: SiteTheme;
}

function read(): Preferences {
  try {
    return storage.read<Preferences>(KEYS.preferences, {});
  } catch {
    return {};
  }
}

export const preferencesService = {
  locale(): Locale {
    const value = read().locale;
    return value && LOCALES.includes(value) ? value : "en";
  },
  setLocale(locale: Locale) {
    if (!LOCALES.includes(locale)) return;
    storage.write<Preferences>(KEYS.preferences, { ...read(), locale });
  },
  siteTheme(): SiteTheme {
    return read().siteTheme === "light" ? "light" : "dark";
  },
  setSiteTheme(siteTheme: SiteTheme) {
    storage.write<Preferences>(KEYS.preferences, { ...read(), siteTheme });
  },
};
