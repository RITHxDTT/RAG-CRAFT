"use client";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { authService } from "@/services/auth.service";
import { errorMessage } from "@/services/api";
import type { CurrentUser } from "@/types/auth";
import { LanguageSwitcher } from "@/components/language-switcher";
import { useT } from "@/i18n/context";
import { useSiteTheme } from "@/features/landing/use-site-theme";
import {
  Sparkles,
  ShieldCheck,
  FileSearch,
  Layers,
  ArrowRight,
  Loader2,
  AlertCircle,
  Lock,
  Mail,
  Moon,
  Sun,
  ArrowLeft,
} from "lucide-react";

export function Login({ onLogin }: { onLogin: (user: CurrentUser) => void }) {
  const t = useT();
  const [theme, toggleTheme] = useSiteTheme();
  const [mode, setMode] = useState<"login" | "register" | "forgot">("login");
  const [resetToken, setResetToken] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    setNotice("");
    try {
      if (mode === "forgot") {
        const result = await authService.forgot(String(data.get("email")));
        setNotice(result.message);
        setResetToken(result.token);
        return;
      }
      if (mode === "register") {
        await authService.register({
          full_name: String(data.get("full_name")),
          email: String(data.get("email")),
          password: String(data.get("password")),
          confirm_password: String(data.get("confirm_password")),
          terms: data.get("terms") === "on",
        });
        setMode("login");
        setNotice(t("auth.accountCreated"));
        return;
      }
      onLogin(await authService.login(String(data.get("email")), String(data.get("password"))));
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  const features = [
    { icon: FileSearch, text: t("auth.feature1") },
    { icon: Layers, text: t("auth.feature2") },
    { icon: ShieldCheck, text: t("auth.feature3") },
  ];

  return (
    <main className="login-layout">
      <section className="login-intro">
        <Link href="/" className="login-back">
          <ArrowLeft size={14} />
          {t("auth.backHome")}
        </Link>
        <div className="brand-mark">RC</div>
        <p className="eyebrow">
          <Sparkles style={{ width: "13px", height: "13px" }} />
          {t("auth.platform")}
        </p>
        <h1>
          {t("auth.heroTitle1")}
          <br />
          {t("auth.heroTitle2")}
        </h1>
        <p>{t("auth.heroText")}</p>

        <div className="feature-pill-list">
          {features.map(({ icon: Icon, text }) => (
            <div className="feature-pill" key={text}>
              <div className="feature-pill-icon">
                <Icon style={{ width: "15px", height: "15px" }} />
              </div>
              <span>{text}</span>
            </div>
          ))}
        </div>

        <div className="login-footnote">{t("auth.footnote")}</div>
      </section>

      <section className="login-form">
        <div className="login-card">
          <div className="login-card-top">
            <p className="eyebrow">
              <Lock style={{ width: "12px", height: "12px" }} />
              {t("auth.demoAccess")}
            </p>
            <div className="rc-inline" style={{ gap: 8 }}>
              <LanguageSwitcher compact />
              <button
                type="button"
                className="rc-icon-button"
                onClick={toggleTheme}
                aria-label={theme === "dark" ? t("theme.switchToLight") : t("theme.switchToDark")}
                title={theme === "dark" ? t("theme.switchToLight") : t("theme.switchToDark")}
              >
                {theme === "dark" ? <Sun size={15} /> : <Moon size={15} />}
              </button>
            </div>
          </div>
          <h2>
            {mode === "register" ? t("auth.registerTitle") : mode === "forgot" ? t("auth.forgotTitle") : t("auth.signInTitle")}
          </h2>
          <p className="muted">{t("auth.subtitle")}</p>

          {mode === "login" && (
            <div className="info-note">
              <strong>{t("auth.demoAccounts")}</strong>
              <p>{t("auth.admin")}: admin@gmail.com · {t("auth.password")}: 123</p>
              <p>{t("auth.user")}: user@gmail.com · {t("auth.password")}: 123</p>
            </div>
          )}
          {resetToken && mode === "forgot" && (
            <div className="info-note">
              <p>{t("auth.verificationDone")}</p>
              <a className="primary" href={`/reset-password#token=${encodeURIComponent(resetToken)}`}>
                {t("auth.continueReset")}
              </a>
            </div>
          )}
          <form onSubmit={submit} className="form-stack">
            {mode === "register" && (
              <label>
                {t("auth.fullName")}
                <input name="full_name" required maxLength={120} autoComplete="name" />
              </label>
            )}
            <label>
              <span>{t("auth.email")}</span>
              <div className="rc-input-icon">
                <input
                  name="email"
                  type="email"
                  autoComplete="username"
                  placeholder="you@company.com"
                  required
                  maxLength={254}
                />
                <Mail />
              </div>
            </label>

            {mode !== "forgot" && (
              <label>
                <span>{t("auth.password")}</span>
                <div className="rc-input-icon">
                  <input
                    name="password"
                    type="password"
                    autoComplete="current-password"
                    placeholder="••••••••••••"
                    required
                    maxLength={256}
                  />
                  <Lock />
                </div>
              </label>
            )}
            {mode === "register" && (
              <label>
                {t("auth.confirmPassword")}
                <input name="confirm_password" type="password" required minLength={1} maxLength={256} autoComplete="new-password" />
              </label>
            )}
            {mode === "register" && (
              <label className="checkbox-label">
                <input type="checkbox" name="terms" required />
                {t("auth.terms")}
              </label>
            )}
            {notice && (
              <p className="success-note" role="status">{t(notice)}</p>
            )}
            {error && (
              <p role="alert" className="error">
                <AlertCircle style={{ width: "16px", height: "16px", flexShrink: 0 }} />
                {t(error)}
              </p>
            )}

            <button className="primary" disabled={busy} style={{ width: "100%", padding: "11px", marginTop: "6px" }}>
              {busy ? (
                <>
                  <Loader2 style={{ width: "16px", height: "16px", animation: "spin 1s linear infinite" }} />
                  {t("common.pleaseWait")}
                </>
              ) : (
                <>
                  {mode === "register" ? t("auth.createAccount") : mode === "forgot" ? t("auth.simulateVerification") : t("auth.signIn")}
                  <ArrowRight style={{ width: "15px", height: "15px" }} />
                </>
              )}
            </button>
          </form>

          <div className="actions" style={{ marginTop: 20 }}>
            {(["login", "register", "forgot"] as const)
              .filter((item) => item !== mode)
              .map((item) => (
                <button
                  className="text-button"
                  disabled={busy}
                  key={item}
                  onClick={() => {
                    setMode(item);
                    setError("");
                    setNotice("");
                  }}
                >
                  {item === "login" ? t("auth.signIn") : item === "register" ? t("auth.createAccount") : t("auth.forgotPassword")}
                </button>
              ))}
          </div>
        </div>
      </section>
    </main>
  );
}
