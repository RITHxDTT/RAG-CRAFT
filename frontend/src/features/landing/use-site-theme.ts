"use client";
import { useCallback, useEffect, useSyncExternalStore } from "react";
import { preferencesService, type SiteTheme } from "@/services/preferences.service";

// Theme for pre-login pages (landing, sign in). Stored as a browser preference and
// mirrored to the `.dark` class on <html> so Tailwind `dark:` utilities respond.
const listeners = new Set<() => void>();
const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};
const getSnapshot = () => preferencesService.siteTheme();
const getServerSnapshot = (): SiteTheme => "dark";

export function applyHtmlTheme(dark: boolean) {
  document.documentElement.classList.toggle("dark", dark);
}

export function useSiteTheme(): [SiteTheme, () => void] {
  const theme = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    applyHtmlTheme(theme === "dark");
  }, [theme]);

  const toggle = useCallback(() => {
    preferencesService.setSiteTheme(preferencesService.siteTheme() === "dark" ? "light" : "dark");
    listeners.forEach((listener) => listener());
  }, []);

  return [theme, toggle];
}
