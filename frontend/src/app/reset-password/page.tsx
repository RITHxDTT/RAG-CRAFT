"use client";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { authService } from "@/services/auth.service";
import { errorMessage } from "@/services/api";
import { LanguageSwitcher } from "@/components/language-switcher";
import { useT } from "@/i18n/context";

export default function ResetPassword() {
  const t = useT();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const data = new FormData(event.currentTarget);
    try {
      await authService.reset(
        new URLSearchParams(window.location.hash.slice(1)).get("token") || "",
        String(data.get("password")),
        String(data.get("confirm")),
      );
      setDone(true);
      window.history.replaceState(null, "", window.location.pathname);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="page-content" style={{ maxWidth: 560, margin: "60px auto" }}>
      <section className="panel">
        <div className="login-card-top">
          <p className="eyebrow">RAG CRAFT</p>
          <LanguageSwitcher compact />
        </div>
        <h1>{t("reset.title")}</h1>
        <p className="muted">{t("reset.text")}</p>
        {done ? (
          <p className="success-note">
            {t("reset.done")} <Link href="/">{t("reset.signIn")}</Link>
          </p>
        ) : (
          <form className="form-stack" onSubmit={submit}>
            <label>
              {t("reset.newPassword")}
              <input name="password" type="password" minLength={1} maxLength={256} autoComplete="new-password" required />
            </label>
            <label>
              {t("reset.confirm")}
              <input name="confirm" type="password" minLength={1} maxLength={256} autoComplete="new-password" required />
            </label>
            {error && <p className="error" role="alert">{t(error)}</p>}
            <button disabled={busy} className="primary">{busy ? t("common.saving") : t("reset.button")}</button>
          </form>
        )}
      </section>
    </main>
  );
}
