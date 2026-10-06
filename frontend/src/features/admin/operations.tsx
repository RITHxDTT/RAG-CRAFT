"use client";
import { useT } from "@/i18n/context";
import { Monitor, Server, Database, Boxes, Brain } from "lucide-react";

const SERVICES = [
  { key: "frontend", icon: Monitor, online: true },
  { key: "backend", icon: Server, online: false },
  { key: "postgres", icon: Database, online: false },
  { key: "qdrant", icon: Boxes, online: false },
  { key: "ollama", icon: Brain, online: false },
] as const;

export function Operations() {
  const t = useT();
  const names: Record<(typeof SERVICES)[number]["key"], string> = {
    frontend: t("ops.frontend"),
    backend: t("ops.backend"),
    postgres: "PostgreSQL",
    qdrant: "Qdrant",
    ollama: "Ollama",
  };
  return (
    <section className="panel">
      <h2>{t("ops.title")}</h2>
      <p className="muted">{t("ops.text")}</p>
      <div className="stat-grid rc-ops-grid">
        {SERVICES.map(({ key, icon: Icon, online }) => (
          <article className={`stat rc-ops-card ${online ? "online" : ""}`} key={key}>
            <span className="rc-ops-icon"><Icon size={18} /></span>
            <h3>{names[key]}</h3>
            <span className={`badge ${online ? "badge-active" : "badge-inactive"}`}>
              <span className="badge-dot" aria-hidden="true" />
              {online ? t("ops.online") : t("ops.notConnected")}
            </span>
          </article>
        ))}
      </div>
    </section>
  );
}
