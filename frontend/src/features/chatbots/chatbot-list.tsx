"use client";
import { useState } from "react";
import type { Chatbot } from "@/types/chatbot";
import { StatusBadge } from "@/components/status-badge";
import { useLabel, useLocale } from "@/i18n/context";
import {
  Bot,
  FileText,
  ArrowUpRight,
  Plus,
  Cpu,
  Sparkles,
  CheckCircle2,
  AlertCircle,
  Search,
} from "lucide-react";

const STATUSES = ["ALL", "DRAFT", "ACTIVE", "INACTIVE", "ERROR"] as const;

export function ChatbotList({
  bots,
  onSelect,
  onCreate,
}: {
  bots: Chatbot[];
  onSelect: (bot: Chatbot) => void;
  onCreate: () => void;
}) {
  const { t, formatDate } = useLocale();
  const labelFor = useLabel();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<(typeof STATUSES)[number]>("ALL");
  const filtered = bots.filter(
    (bot) => bot.name.toLowerCase().includes(search.toLowerCase()) && (status === "ALL" || bot.status === status),
  );

  if (!bots.length)
    return (
      <section className="panel empty-state modern-empty-card">
        <div className="empty-icon-wrap glow-teal">
          <Bot style={{ width: "32px", height: "32px" }} />
        </div>
        <h2>{t("bots.empty.title")}</h2>
        <p>{t("bots.empty.text")}</p>
        <button className="primary" onClick={onCreate}>
          <Plus style={{ width: "16px", height: "16px" }} />
          {t("page.createChatbot")}
        </button>
      </section>
    );

  return (
    <>
      <div className="actions rc-filter-row" style={{ marginBottom: 16 }}>
        <div className="rc-input-icon rc-search">
          <input aria-label={t("bots.search")} placeholder={t("bots.search")} value={search} onChange={(e) => setSearch(e.target.value)} />
          <Search />
        </div>
        <select aria-label={t("bots.filter")} value={status} onChange={(e) => setStatus(e.target.value as (typeof STATUSES)[number])}>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {value === "ALL" ? t("bots.filter.ALL") : labelFor(value)}
            </option>
          ))}
        </select>
      </div>
      {!filtered.length && <p className="empty-state">{t("bots.noMatch")}</p>}
      <div className="bot-grid">
        {filtered.map((bot) => {
          const docCount = bot.document_count ?? 0;
          const readyCount = bot.ready_count ?? 0;
          const failedCount = bot.failed_count ?? 0;
          const readyPercent = docCount > 0 ? Math.round((readyCount / docCount) * 100) : 0;
          const modelName = bot.settings?.model_name || t("bots.generalModel");
          const tone = bot.settings?.tone;

          return (
            <div
              key={bot.id}
              className="bot-card-modern"
              onClick={() => onSelect(bot)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  onSelect(bot);
                }
              }}
            >
              <div className="bot-card-header">
                <div className="bot-avatar-wrap">
                  <div className="bot-avatar-inner">{bot.avatar || bot.name.slice(0, 2).toUpperCase()}</div>
                  <span className={`status-indicator-dot ${bot.status === "ACTIVE" ? "online" : "offline"}`} />
                </div>
                <div className="bot-card-badges">
                  <StatusBadge status={bot.status} />
                </div>
              </div>

              <div className="bot-card-body">
                <h3 className="bot-card-title">{bot.name}</h3>
                <p className="bot-card-desc">{bot.description || t("bots.noDescription")}</p>
              </div>

              <div className="bot-card-tags">
                <span className="bot-tag">
                  <Cpu style={{ width: "12px", height: "12px" }} />
                  <span className="truncate max-w-[120px]">{modelName}</span>
                </span>
                {tone && (
                  <span className="bot-tag tone-tag">
                    <Sparkles style={{ width: "11px", height: "11px" }} />
                    {t(`tone.${tone}`)}
                  </span>
                )}
              </div>

              <div className="bot-card-health">
                <div className="health-label-row">
                  <span className="health-label">
                    <FileText style={{ width: "12px", height: "12px" }} />
                    {docCount === 1 ? t("bots.documentOne", { count: docCount }) : t("bots.documentMany", { count: docCount })}
                  </span>
                  <span className="health-status-text">
                    {docCount === 0 ? (
                      t("bots.noDocs")
                    ) : failedCount > 0 ? (
                      <span className="rc-health-bad">
                        <AlertCircle style={{ width: "11px", height: "11px" }} />
                        {t("bots.failed", { count: failedCount })}
                      </span>
                    ) : (
                      <span className="rc-health-good">
                        <CheckCircle2 style={{ width: "11px", height: "11px" }} />
                        {t("bots.ready", { count: readyCount })}
                      </span>
                    )}
                  </span>
                </div>
                <div className="health-bar-track">
                  <div
                    className={`health-bar-fill ${failedCount > 0 ? "has-failed" : ""}`}
                    style={{ width: `${docCount === 0 ? 0 : readyPercent}%` }}
                  />
                </div>
              </div>

              <div className="bot-card-footer">
                <span className="bot-card-date">{t("bots.updated", { date: formatDate(bot.updated_at || bot.created_at) })}</span>
                <span className="bot-card-action">
                  {t("bots.open")}
                  <ArrowUpRight style={{ width: "14px", height: "14px" }} />
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}
