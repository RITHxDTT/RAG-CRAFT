"use client";
import { useState } from "react";
import type { Chatbot } from "@/types/chatbot";
import { chatbotService } from "@/services/chatbot.service";
import { appealService } from "@/services/appeal.service";
import { inputDialog } from "@/components/confirm-dialog";
import { errorMessage } from "@/services/api";
import { StatusBadge } from "@/components/status-badge";
import { KnowledgeBase } from "@/features/knowledge/knowledge-base";
import { Channels } from "@/features/channels/channels";
import { Conversations } from "@/features/conversations/conversations";
import { Playground } from "@/features/playground/playground";
import { ChatbotForm } from "./chatbot-form";
import { useLocale } from "@/i18n/context";
import {
  ArrowLeft,
  Layers,
  Database,
  Sparkles,
  Settings,
  FileText,
  CheckCircle2,
  AlertCircle,
  AlertTriangle,
  Cpu,
  Calendar,
  ShieldCheck,
  Trash2,
  Loader2,
  Copy,
  Power,
} from "lucide-react";

type Tab = "overview" | "knowledge" | "playground" | "conversations" | "channels" | "settings";

export function ChatbotDetail({
  bot,
  onChanged,
  onBack,
  onDeleted,
}: {
  bot: Chatbot;
  onChanged: (bot: Chatbot) => void;
  onBack: () => void;
  onDeleted: () => void;
}) {
  const { t, formatDate } = useLocale();
  const [tab, setTab] = useState<Tab>("overview");
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function refreshKnowledge() {
    try {
      onChanged(await chatbotService.get(bot.id));
    } catch (error) {
      setError(errorMessage(error));
    }
  }

  async function run(action: () => Promise<Chatbot>) {
    setBusy(true);
    setError("");
    try {
      onChanged(await action());
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function appeal() {
    const message = await inputDialog({ message: t("detail.disabledBanner", { reason: bot.disabled_reason || "—" }), inputLabel: t("detail.appeal.message"), multiline: true, maxLength: 500, confirmLabel: t("detail.appeal"), danger: false });
    if (message === null) return;
    setBusy(true);
    setError("");
    try {
      await appealService.submit(bot.id, message);
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    // Deleting requires retyping the chatbot name.
    const typed = await inputDialog({ title: t("detail.delete.title"), message: t("detail.delete.question", { name: bot.name }), inputLabel: t("detail.delete.typeName"), expect: bot.name, confirmLabel: t("detail.delete.confirm") });
    if (typed === null) {
      setConfirm(false);
      return;
    }
    setBusy(true);
    setError("");
    try {
      await chatbotService.delete(bot.id, typed);
      onDeleted();
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  const tabs: { id: Tab; icon: typeof Layers }[] = [
    { id: "overview", icon: Layers },
    { id: "knowledge", icon: Database },
    { id: "playground", icon: Sparkles },
    { id: "conversations", icon: FileText },
    { id: "channels", icon: Layers },
    { id: "settings", icon: Settings },
  ];

  return (
    <>
      <button className="text-button back-link" onClick={onBack}>
        <ArrowLeft style={{ width: "15px", height: "15px" }} />
        {t("detail.allChatbots")}
      </button>

      <header className="page-heading">
        <div>
          <div className="title-row">
            <h1>{bot.name}</h1>
            <StatusBadge status={bot.status} />
          </div>
          <p className="muted">{bot.description || t("detail.configureHint")}</p>
        </div>
        <div className="actions">
          <button disabled={busy} onClick={() => void run(() => chatbotService.duplicate(bot.id))}>
            <Copy size={14} />
            {t("detail.duplicate")}
          </button>
          {bot.status === "DISABLED" ? (
            <button disabled={busy} className="primary" onClick={() => void appeal()}>
              <Power size={14} />
              {t("detail.appeal")}
            </button>
          ) : bot.status !== "DRAFT" && (
            <button
              disabled={busy}
              className={bot.status === "ACTIVE" ? "" : "primary"}
              onClick={() => void run(() => (bot.status === "ACTIVE" ? chatbotService.pause(bot.id) : chatbotService.publish(bot.id)))}
            >
              <Power size={14} />
              {bot.status === "ACTIVE" ? t("detail.pause") : bot.status === "PAUSED" ? t("detail.resume") : t("detail.publish")}
            </button>
          )}
        </div>
      </header>
      {bot.status === "DISABLED" && <p className="error" role="alert">{t("detail.disabledBanner", { reason: bot.disabled_reason || "—" })}</p>}

      <nav className="tabs" aria-label="Chatbot sections">
        {tabs.map(({ id, icon: Icon }) => (
          <button key={id} onClick={() => setTab(id)} aria-current={tab === id ? "page" : undefined} className={tab === id ? "selected" : ""}>
            <Icon style={{ width: "15px", height: "15px" }} aria-hidden="true" />
            {t(`detail.tab.${id}`)}
          </button>
        ))}
      </nav>

      {error && (
        <p className="error" role="alert" style={{ marginBottom: "20px" }}>
          <AlertCircle style={{ width: "16px", height: "16px", flexShrink: 0 }} />
          {t(error)}
        </p>
      )}

      {tab === "overview" && (
        <>
          <div className="stat-grid three">
            <div className="stat">
              <div className="stat-header">
                <p>{t("detail.totalDocs")}</p>
                <div className="stat-icon tint-blue"><FileText style={{ width: "18px", height: "18px" }} /></div>
              </div>
              <strong>{bot.document_count}</strong>
              <span>{t("detail.totalDocsHint")}</span>
            </div>
            <div className="stat">
              <div className="stat-header">
                <p>{t("detail.ready")}</p>
                <div className="stat-icon tint-green"><CheckCircle2 style={{ width: "18px", height: "18px" }} /></div>
              </div>
              <strong>{bot.ready_count}</strong>
              <span>{t("detail.readyHint")}</span>
            </div>
            <div className="stat">
              <div className="stat-header">
                <p>{t("detail.attention")}</p>
                <div className="stat-icon tint-rose"><AlertCircle style={{ width: "18px", height: "18px" }} /></div>
              </div>
              <strong>{bot.failed_count}</strong>
              <span>{t("detail.attentionHint")}</span>
            </div>
          </div>

          <section className="panel">
            <div className="section-heading" style={{ marginBottom: "16px" }}>
              <h2>{t("detail.about")}</h2>
            </div>
            <dl className="detail-grid">
              <div>
                <dt className="rc-inline"><Cpu style={{ width: "13px", height: "13px" }} />{t("detail.model")}</dt>
                <dd>{bot.settings.model_name}</dd>
              </div>
              <div>
                <dt className="rc-inline"><Calendar style={{ width: "13px", height: "13px" }} />{t("detail.createdAt")}</dt>
                <dd>{formatDate(bot.created_at)}</dd>
              </div>
              <div>
                <dt className="rc-inline"><ShieldCheck style={{ width: "13px", height: "13px" }} />{t("detail.knowledgeStatus")}</dt>
                <dd>{bot.ready_count ? t("detail.readyDocs") : bot.document_count ? t("detail.awaiting") : t("detail.noKnowledge")}</dd>
              </div>
              <div>
                <dt className="rc-inline"><Layers style={{ width: "13px", height: "13px" }} />{t("detail.scope")}</dt>
                <dd>{t("detail.scopeText")}</dd>
              </div>
            </dl>
          </section>
        </>
      )}

      {tab === "knowledge" && <KnowledgeBase chatbotId={bot.id} onChanged={() => void refreshKnowledge()} />}
      {tab === "playground" && (
        <Playground chatbotId={bot.id} name={bot.name} starterQuestions={bot.starter_questions} active={bot.status === "ACTIVE"} />
      )}
      {tab === "channels" && <Channels chatbotId={bot.id} />}
      {tab === "conversations" && <Conversations chatbotId={bot.id} />}
      {tab === "settings" && (
        <>
          <ChatbotForm
            key={bot.updated_at}
            bot={bot}
            onSaved={(updated) => {
              onChanged(updated);
              setTab("overview");
            }}
            onCancel={() => setTab("overview")}
          />

          <section className="panel danger-zone">
            <h3>
              <AlertTriangle style={{ width: "18px", height: "18px" }} />
              {t("detail.delete.title")}
            </h3>
            <p className="muted" style={{ marginBottom: "16px" }}>{t("detail.delete.text")}</p>
            {confirm ? (
              <div className="actions">
                <span className="rc-danger-text">{t("detail.delete.question", { name: bot.name })}</span>
                <button className="danger" disabled={busy} onClick={() => void remove()}>
                  {busy ? (
                    <>
                      <Loader2 style={{ width: "14px", height: "14px", animation: "spin 1s linear infinite" }} />
                      {t("common.deleting")}
                    </>
                  ) : (
                    <>
                      <Trash2 style={{ width: "14px", height: "14px" }} />
                      {t("detail.delete.confirm")}
                    </>
                  )}
                </button>
                <button disabled={busy} onClick={() => setConfirm(false)}>{t("detail.delete.keep")}</button>
              </div>
            ) : (
              <button className="danger" onClick={() => setConfirm(true)}>
                <Trash2 style={{ width: "14px", height: "14px" }} />
                {t("detail.delete.title")}
              </button>
            )}
          </section>
        </>
      )}
    </>
  );
}
