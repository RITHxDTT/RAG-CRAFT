"use client";
import { useRef, useState, type FormEvent } from "react";
import { Camera, Trash2, Loader2, Save, User as UserIcon, SlidersHorizontal, Lock, AlertTriangle, AlertCircle } from "lucide-react";
import type { CurrentUser } from "@/types/auth";
import { userService } from "@/services/user.service";
import { demoService } from "@/services/demo.service";
import { errorMessage } from "@/services/api";
import { Avatar } from "@/components/avatar";
import { useLocale } from "@/i18n/context";
import { LOCALES } from "@/services/preferences.service";

const ACCEPTED = ["image/png", "image/jpeg", "image/webp"];
const MAX_BYTES = 500000;

export function Profile({ user, onChanged }: { user: CurrentUser; onChanged: (user: CurrentUser) => void }) {
  const { t, locale, setLocale } = useLocale();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [avatar, setAvatar] = useState(user.avatar || "");
  const [fullName, setFullName] = useState(user.full_name);
  const [displayName, setDisplayName] = useState(user.display_name || "");
  const [reset, setReset] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  function pickFile(file?: File) {
    if (!file) return;
    if (file.size > MAX_BYTES || !ACCEPTED.includes(file.type)) {
      setError(t("profile.photoError"));
      return;
    }
    setError("");
    const reader = new FileReader();
    reader.onload = () => setAvatar(String(reader.result));
    reader.readAsDataURL(file);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      onChanged(
        await userService.updateProfile(
          {
            full_name: String(data.get("name")),
            email: String(data.get("email")),
            display_name: String(data.get("display")),
            bio: String(data.get("bio")),
            avatar,
            theme: String(data.get("theme")) as "light" | "dark",
          },
          String(data.get("password")),
          String(data.get("confirm")),
          { currentPassword: String(data.get("current") || "") },
        ),
      );
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  const previewName = displayName || fullName;

  return (
    <>
      <section className="panel rc-profile">
        <div className="section-heading" style={{ marginBottom: 4 }}>
          <div>
            <h2>{t("profile.title")}</h2>
            <p className="muted section-subtitle">{t("profile.subtitle")}</p>
          </div>
        </div>

        <form className="form-stack" onSubmit={save}>
          {/* Photo */}
          <div className="rc-profile-hero">
            <div className="rc-profile-avatar">
              <Avatar name={previewName} email={user.email} image={avatar} size={88} radius={28} title={t("profile.pictureAlt")} />
              <button
                type="button"
                className="rc-avatar-edit"
                onClick={() => fileInput.current?.click()}
                aria-label={avatar ? t("profile.changePhoto") : t("profile.uploadPhoto")}
                title={avatar ? t("profile.changePhoto") : t("profile.uploadPhoto")}
              >
                <Camera size={14} />
              </button>
            </div>
            <div className="rc-profile-hero-text">
              <strong>{previewName || user.email}</strong>
              <span>{user.email}</span>
              <span className={`badge rc-role-badge role-${user.role.toLowerCase()}`}>{t(`role.${user.role}`)}</span>
              <p className="muted">{t("profile.photoHint")}</p>
              <div className="actions" style={{ marginTop: 4 }}>
                <button type="button" onClick={() => fileInput.current?.click()}>
                  <Camera size={14} />
                  {avatar ? t("profile.changePhoto") : t("profile.uploadPhoto")}
                </button>
                {avatar && (
                  <button type="button" className="text-button danger" onClick={() => setAvatar("")}>
                    <Trash2 size={14} />
                    {t("profile.removePhoto")}
                  </button>
                )}
              </div>
              <input
                ref={fileInput}
                type="file"
                accept={ACCEPTED.join(",")}
                className="sr-only"
                tabIndex={-1}
                onChange={(event) => {
                  pickFile(event.target.files?.[0]);
                  event.target.value = "";
                }}
              />
            </div>
          </div>

          {/* Personal information */}
          <h3 className="rc-form-section"><UserIcon size={15} />{t("profile.personal")}</h3>
          <div className="form-columns two">
            <label>
              {t("profile.fullName")}
              <input name="name" value={fullName} onChange={(event) => setFullName(event.target.value)} required maxLength={120} />
            </label>
            <label>
              {t("profile.email")}
              <input name="email" type="email" defaultValue={user.email} required />
            </label>
            <label>
              {t("profile.displayName")}
              <span className="muted rc-hint">{t("profile.displayNameHint")}</span>
              <input name="display" value={displayName} onChange={(event) => setDisplayName(event.target.value)} maxLength={120} />
            </label>
          </div>
          <label>
            {t("profile.bio")}
            <textarea name="bio" defaultValue={user.bio} maxLength={2000} rows={3} />
          </label>

          {/* Preferences */}
          <h3 className="rc-form-section"><SlidersHorizontal size={15} />{t("profile.preferences")}</h3>
          <div className="form-columns two">
            <label>
              {t("profile.theme")}
              <select name="theme" defaultValue={user.theme || "light"}>
                <option value="light">{t("menu.light")}</option>
                <option value="dark">{t("menu.dark")}</option>
              </select>
            </label>
            <label>
              {t("profile.language")}
              <select value={locale} onChange={(event) => setLocale(event.target.value as typeof locale)}>
                {LOCALES.map((item) => (
                  <option key={item} value={item}>{t(`lang.${item}`)}</option>
                ))}
              </select>
            </label>
          </div>

          {/* Security */}
          <h3 className="rc-form-section"><Lock size={15} />{t("profile.security")}</h3>
          <div className="form-columns two">
            <label>
              {t("profile.currentPassword")}
              <input name="current" type="password" autoComplete="current-password" />
            </label>
            <label>
              {t("profile.newPassword")}
              <input name="password" type="password" autoComplete="new-password" />
            </label>
            <label>
              {t("profile.confirmPassword")}
              <input name="confirm" type="password" autoComplete="new-password" />
            </label>
          </div>

          {error && (
            <p role="alert" className="error">
              <AlertCircle size={16} style={{ flexShrink: 0 }} />
              {t(error)}
            </p>
          )}
          <div className="actions">
            <button className="primary" disabled={busy}>
              {busy ? <Loader2 size={15} style={{ animation: "spin 1s linear infinite" }} /> : <Save size={15} />}
              {busy ? t("common.saving") : t("profile.save")}
            </button>
          </div>
        </form>
      </section>

      <section className="panel danger-zone">
        <h3><AlertTriangle size={18} />{t("profile.reset.title")}</h3>
        <p className="muted" style={{ marginBottom: 16 }}>{t("profile.reset.text")}</p>
        {reset ? (
          <div className="actions">
            <button
              className="danger"
              disabled={busy}
              onClick={async () => {
                setBusy(true);
                try {
                  await demoService.reset();
                } catch (error) {
                  setError(errorMessage(error));
                  setBusy(false);
                }
              }}
            >
              {t("profile.reset.confirm")}
            </button>
            <button onClick={() => setReset(false)}>{t("common.cancel")}</button>
          </div>
        ) : (
          <button className="danger" onClick={() => setReset(true)}>{t("profile.reset.button")}</button>
        )}
      </section>
    </>
  );
}
