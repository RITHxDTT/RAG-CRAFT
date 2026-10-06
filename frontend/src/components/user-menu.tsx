"use client";
import { useEffect, useRef, useState } from "react";
import { ChevronDown, LogOut, Moon, Settings, Sun, Languages, Check } from "lucide-react";
import type { CurrentUser } from "@/types/auth";
import { Avatar } from "./avatar";
import { useLocale } from "@/i18n/context";
import { LOCALES } from "@/services/preferences.service";

export function UserMenu({
  user,
  onOpenProfile,
  onToggleTheme,
  onLogout,
  loggingOut,
}: {
  user: CurrentUser;
  onOpenProfile: () => void;
  onToggleTheme: () => void;
  onLogout: () => void;
  loggingOut: boolean;
}) {
  const { t, locale, setLocale } = useLocale();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const dark = user.theme === "dark";
  const name = user.display_name || user.full_name || (user.role === "ADMIN" ? t("nav.administrator") : t("nav.member"));

  useEffect(() => {
    if (!open) return;
    const away = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    const key = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", away);
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("mousedown", away);
      document.removeEventListener("keydown", key);
    };
  }, [open]);

  return (
    <div className="rc-user-menu" ref={root}>
      <button
        type="button"
        className="rc-user-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t("topbar.openMenu")}
        onClick={() => setOpen((value) => !value)}
      >
        <Avatar name={name} email={user.email} image={user.avatar} size={30} />
        <span className="rc-user-name">
          <strong>{name}</strong>
          <small>{t(`role.${user.role}`)}</small>
        </span>
        <ChevronDown size={14} className={`rc-chevron ${open ? "open" : ""}`} aria-hidden="true" />
      </button>

      {open && (
        <div className="rc-menu" role="menu">
          <div className="rc-menu-header">
            <Avatar name={name} email={user.email} image={user.avatar} size={40} />
            <div>
              <small>{t("menu.signedInAs")}</small>
              <strong>{user.full_name || name}</strong>
              <span>{user.email}</span>
            </div>
          </div>

          <button type="button" role="menuitem" onClick={() => { setOpen(false); onOpenProfile(); }}>
            <Settings size={15} />
            {t("menu.profile")}
          </button>

          <button type="button" role="menuitem" onClick={onToggleTheme}>
            {dark ? <Sun size={15} /> : <Moon size={15} />}
            {t("menu.theme")}
            <span className="rc-menu-value">{dark ? t("menu.dark") : t("menu.light")}</span>
          </button>

          <div className="rc-menu-group">
            <span className="rc-menu-label">
              <Languages size={14} />
              {t("menu.language")}
            </span>
            {LOCALES.map((item) => (
              <button
                key={item}
                type="button"
                role="menuitemradio"
                aria-checked={item === locale}
                className={item === locale ? "selected" : ""}
                onClick={() => setLocale(item)}
              >
                {t(`lang.${item}`)}
                {item === locale && <Check size={14} className="rc-menu-check" />}
              </button>
            ))}
          </div>

          <div className="rc-menu-divider" />
          <button type="button" role="menuitem" className="rc-menu-danger" disabled={loggingOut} onClick={onLogout}>
            <LogOut size={15} />
            {loggingOut ? t("nav.signingOut") : t("nav.signOut")}
          </button>
        </div>
      )}
    </div>
  );
}
