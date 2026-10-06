"use client";
import { createContext, useCallback, useContext, useEffect, useMemo, useSyncExternalStore, type ReactNode } from "react";
import { en, type DictionaryKey } from "./en";
import { ko, koMessages } from "./ko";
import { preferencesService, type Locale } from "@/services/preferences.service";
import { KEYS } from "@/storage/keys";

type Vars = Record<string, string | number>;

interface LocaleContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  /** Translate a dictionary key (or a raw service message) with optional {placeholders}. */
  t: (key: DictionaryKey | string, vars?: Vars) => string;
  /** Format helpers that follow the active locale. */
  formatDate: (value: string | number | Date) => string;
  formatDateTime: (value: string | number | Date) => string;
  formatNumber: (value: number) => string;
}

const dictionaries = { en, ko } as const;
const messages: Record<Locale, Record<string, string>> = { en: {}, ko: koMessages };
const tags: Record<Locale, string> = { en: "en-US", ko: "ko-KR" };

const LocaleContext = createContext<LocaleContextValue | null>(null);

function interpolate(template: string, vars?: Vars) {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in vars ? String(vars[name]) : match,
  );
}

export function translate(locale: Locale, key: string, vars?: Vars) {
  const dictionary = dictionaries[locale] as Record<string, string>;
  const value = dictionary[key] ?? messages[locale][key] ?? (en as Record<string, string>)[key] ?? key;
  return interpolate(value, vars);
}

// The saved locale lives in browser storage, so it is exposed to React as an external store.
const listeners = new Set<() => void>();
function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (!event.key || event.key === KEYS.preferences) listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}
const getSnapshot = () => preferencesService.locale();
// Server markup (and the first client render during hydration) always uses English.
const getServerSnapshot = (): Locale => "en";

export function LocaleProvider({ children }: { children: ReactNode }) {
  const locale = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const setLocale = useCallback((next: Locale) => {
    preferencesService.setLocale(next);
    listeners.forEach((listener) => listener());
  }, []);

  const value = useMemo<LocaleContextValue>(() => {
    const tag = tags[locale];
    return {
      locale,
      setLocale,
      t: (key, vars) => translate(locale, key, vars),
      formatDate: (input) => new Date(input).toLocaleDateString(tag, { year: "numeric", month: "short", day: "numeric" }),
      formatDateTime: (input) => new Date(input).toLocaleString(tag, { year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }),
      formatNumber: (input) => input.toLocaleString(tag),
    };
  }, [locale, setLocale]);

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useLocale() {
  const context = useContext(LocaleContext);
  if (!context) throw new Error("useLocale must be used inside LocaleProvider.");
  return context;
}

/** Shorthand hook returning only the translate function. */
export function useT() {
  return useLocale().t;
}

/** Map raw status / channel / file-type names from data to a translated label. */
export function useLabel() {
  const { t } = useLocale();
  return (name: string) => {
    const normalized = name.toUpperCase().replaceAll(" ", "_");
    for (const prefix of ["status", "channel", "type"]) {
      const key = `${prefix}.${normalized}`;
      const value = translate("en", key);
      if (value !== key) return t(key);
    }
    return name;
  };
}
