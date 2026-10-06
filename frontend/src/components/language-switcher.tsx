"use client";
import { Languages } from "lucide-react";
import { useLocale } from "@/i18n/context";
import { LOCALES, type Locale } from "@/services/preferences.service";

/** Compact EN / 한국어 toggle used on the login page, top bar, and public pages. */
export function LanguageSwitcher({ compact = false }: { compact?: boolean }) {
  const { locale, setLocale, t } = useLocale();
  const short: Record<Locale, string> = { en: "EN", ko: "KO" };
  return (
    <div className={`rc-lang ${compact ? "compact" : ""}`} role="group" aria-label={t("lang.switch")}>
      {!compact && <Languages size={13} aria-hidden="true" />}
      {LOCALES.map((item) => (
        <button
          key={item}
          type="button"
          className={item === locale ? "chosen" : ""}
          aria-pressed={item === locale}
          onClick={() => setLocale(item)}
          title={t(`lang.${item}`)}
        >
          {compact ? short[item] : t(`lang.${item}`)}
        </button>
      ))}
    </div>
  );
}
