"use client";
import { useEffect, useState } from "react";
import { authService } from "@/services/auth.service";
import { ApiError, errorMessage } from "@/services/api";
import type { CurrentUser } from "@/types/auth";
import { Workspace } from "@/features/workspace/workspace";
import { Login } from "./login";
import { useT } from "@/i18n/context";
import { X, Sparkles } from "lucide-react";

export function AdminApp() {
  const t = useT();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    authService
      .me()
      .then((user) => {
        if (active) setUser(user);
      })
      .catch((error) => {
        if (active && (!(error instanceof ApiError) || error.status !== 401)) setError(errorMessage(error));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    const expired = () => setUser(null);
    const refreshSession = () => {
      authService.me().then((next) => { if (active) setUser(next); }).catch(() => { if (active) setUser(null); });
    };
    const storageChanged = (event: StorageEvent) => {
      if (event.key?.startsWith("ragcraft:v2:")) refreshSession();
    };
    window.addEventListener("storage", storageChanged);
    window.addEventListener("focus", refreshSession);
    window.addEventListener("ragcraft:session-expired", expired);
    return () => {
      active = false;
      window.removeEventListener("storage", storageChanged);
      window.removeEventListener("focus", refreshSession);
      window.removeEventListener("ragcraft:session-expired", expired);
    };
  }, []);

  async function logout() {
    try {
      await authService.logout();
      setUser(null);
      setError("");
    } catch (error) {
      setError(errorMessage(error));
    }
  }

  if (loading)
    return (
      <main className="loading-screen" role="status">
        <div className="loading-spinner" />
        <div className="loading-caption">
          <Sparkles style={{ width: "16px", height: "16px" }} />
          {t("app.opening")}
        </div>
      </main>
    );

  return (
    <>
      {error && (
        <div className="global-error" role="alert">
          <span>{t(error)}</span>
          <button onClick={() => setError("")} aria-label={t("app.dismissError")}>
            <X style={{ width: "14px", height: "14px" }} />
          </button>
        </div>
      )}
      {user ? (
        <Workspace user={user} onLogout={logout} onProfileChanged={setUser} />
      ) : (
        <Login
          onLogin={(user) => {
            setUser(user);
            setError("");
          }}
        />
      )}
    </>
  );
}
