"use client";
import { useEffect, useState } from "react";
import { playgroundService } from "@/services/playground.service";
import { errorMessage } from "@/services/api";
import type { Conversation, ConversationDetail } from "@/types/playground";
import { SourceCards } from "@/features/playground/source-cards";
import { confirmDialog } from "@/components/confirm-dialog";
import { useLabel, useLocale } from "@/i18n/context";
import { MessageSquare, Trash2, Bot, User } from "lucide-react";

export function Conversations({ chatbotId }: { chatbotId: string }) {
  const { t, formatDateTime } = useLocale();
  const labelFor = useLabel();
  const [rows, setRows] = useState<Conversation[]>([]);
  const [detail, setDetail] = useState<ConversationDetail | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    playgroundService
      .list(chatbotId)
      .then((rows) => { if (active) setRows(rows); })
      .catch((e) => { if (active) setError(errorMessage(e)); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [chatbotId]);

  async function open(id: string) {
    setBusy(true);
    setError("");
    try {
      setDetail(await playgroundService.get(chatbotId, id));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    if (!(await confirmDialog({ message: t("conv.deleteConfirm"), confirmLabel: t("common.delete") }))) return;
    setBusy(true);
    setError("");
    try {
      await playgroundService.delete(chatbotId, id);
      setRows(rows.filter((r) => r.id !== id));
      setDetail(null);
      setNotice(t("conv.deleted"));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="form-stack">
      <section className="panel">
        <h2>{t("conv.title")}</h2>
        <p className="muted">{t("conv.subtitle")}</p>
        {error && <p className="error" role="alert">{t(error)}</p>}
        {notice && <p className="success-note" role="status">{notice}</p>}
        {busy && <p role="status">{t("common.loading")}</p>}
        {!busy && !rows.length && <p className="empty-state">{t("conv.empty")}</p>}
        <div className="rc-list">
          {rows.map((row) => (
            <div key={row.id} className={`rc-list-row ${detail?.id === row.id ? "selected" : ""}`}>
              <span className="rc-doc-icon"><MessageSquare size={15} /></span>
              <div style={{ minWidth: 0, flex: 1 }}>
                <button className="text-button" disabled={busy} onClick={() => void open(row.id)}>{row.title}</button>
                <p className="muted">
                  {t("conv.meta", { channel: labelFor(row.channel), created: formatDateTime(row.created_at), updated: formatDateTime(row.updated_at) })}
                </p>
              </div>
              <button className="danger" disabled={busy} onClick={() => void remove(row.id)}>
                <Trash2 size={13} />
                {t("common.delete")}
              </button>
            </div>
          ))}
        </div>
      </section>
      {detail && (
        <section className="panel">
          <h2>{detail.title}</h2>
          <div className="chat-history rc-transcript">
            {detail.messages.map((m) => (
              <article key={m.id} className={`chat-message ${m.role.toLowerCase()}`}>
                <div className="rc-inline" style={{ marginBottom: 6 }}>
                  {m.role === "USER" ? <User size={13} /> : <Bot size={13} />}
                  <span className="message-role">{m.role === "USER" ? t("pg.you") : t("pub.assistant")}</span>
                </div>
                <div className="message-content" style={{ whiteSpace: "pre-wrap" }}>{m.content}</div>
                <SourceCards chatbotId={chatbotId} sources={m.sources} />
              </article>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
