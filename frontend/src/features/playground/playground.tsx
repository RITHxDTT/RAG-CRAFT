"use client";
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import type { ChatMessage, Conversation } from "@/types/playground";
import { playgroundService } from "@/services/playground.service";
import { errorMessage } from "@/services/api";
import { Compare, demoModels } from "./compare";
import { SourceCards } from "./source-cards";
import { confirmDialog } from "@/components/confirm-dialog";
import { useT } from "@/i18n/context";
import {
  Bot,
  User,
  Send,
  Plus,
  Trash2,
  AlertCircle,
  AlertTriangle,
  Loader2,
  RefreshCw,
  MessageSquare,
  GitCompare,
} from "lucide-react";

export function Playground({
  chatbotId,
  name,
  starterQuestions = [],
}: {
  chatbotId: string;
  name: string;
  active: boolean;
  starterQuestions?: string[];
}) {
  const t = useT();
  const [model, setModel] = useState("");
  const [compare, setCompare] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [question, setQuestion] = useState("");
  const [pending, setPending] = useState("");
  const [error, setError] = useState("");
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let mounted = true;
    playgroundService
      .list(chatbotId)
      .then(async (all) => {
        const list = all.filter((c) => c.channel === "PLAYGROUND");
        if (!mounted) return;
        setConversations(list);
        if (list[0]) {
          const detail = await playgroundService.get(chatbotId, list[0].id);
          if (mounted) {
            setConversationId(detail.id);
            setMessages(detail.messages);
          }
        }
      })
      .catch((error) => {
        if (mounted) setError(errorMessage(error));
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [chatbotId]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [messages.length, pending]);

  function refreshList() {
    void playgroundService
      .list(chatbotId)
      .then((list) => setConversations(list.filter((c) => c.channel === "PLAYGROUND")))
      .catch((error) => setError(errorMessage(error)));
  }

  async function newChat(skipConfirmation = false) {
    if (!skipConfirmation && messages.length && !(await confirmDialog({ message: t("pg.newChatConfirm"), confirmLabel: t("pg.newChat"), danger: false }))) return;
    setConversationId(null);
    setMessages([]);
    setQuestion("");
    setError("");
    setConfirmDelete(false);
  }

  async function selectConversation(id: string) {
    if (!id) {
      void newChat();
      return;
    }
    setLoading(true);
    setError("");
    setConfirmDelete(false);
    try {
      const detail = await playgroundService.get(chatbotId, id);
      setConversationId(id);
      setMessages(detail.messages);
      setQuestion("");
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function removeConversation() {
    if (!conversationId) return;
    setLoading(true);
    setError("");
    try {
      await playgroundService.delete(chatbotId, conversationId);
      void newChat(true);
      refreshList();
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function send(event?: FormEvent<HTMLFormElement>) {
    if (event) event.preventDefault();
    const text = question.trim();
    if (!text || pending || loading) return;
    setPending(text);
    setQuestion("");
    setError("");
    try {
      const response = await playgroundService.ask(chatbotId, text, conversationId, model || undefined);
      setConversationId(response.conversation_id);
      setMessages((previous) => [
        ...previous,
        { id: response.user_message_id, role: "USER", content: text, sources: [] },
        { id: response.message_id, role: "ASSISTANT", content: response.answer, sources: response.sources },
      ]);
      refreshList();
    } catch (error) {
      setError(errorMessage(error));
      setQuestion(text);
    } finally {
      setPending("");
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void send();
    }
  }

  return (
    <section className="panel playground-panel">
      <div className="section-heading">
        <div>
          <h2>{t("pg.title")}</h2>
          <p className="muted section-subtitle">{t("pg.subtitle", { name })}</p>
        </div>
        <button disabled={!!pending || loading} onClick={() => void newChat()}>
          <Plus style={{ width: "15px", height: "15px" }} />
          {t("pg.newChat")}
        </button>
      </div>

      <div className="conversation-controls">
        <label>
          <span className="rc-inline">
            <MessageSquare style={{ width: "13px", height: "13px", color: "#0d9488" }} />
            {t("pg.session")}
          </span>
          <select value={conversationId || ""} disabled={!!pending || loading} onChange={(event) => void selectConversation(event.target.value)}>
            <option value="">{t("pg.newConversation")}</option>
            {conversations.map((conversation) => (
              <option key={conversation.id} value={conversation.id}>{conversation.title}</option>
            ))}
          </select>
        </label>
        {conversationId && (
          <button className="text-button danger rc-inline" disabled={!!pending || loading} onClick={() => setConfirmDelete(true)}>
            <Trash2 style={{ width: "13px", height: "13px" }} />
            {t("pg.deleteChat")}
          </button>
        )}
      </div>

      {confirmDelete && (
        <div className="info-note rc-note-danger">
          <div className="rc-inline">
            <AlertTriangle style={{ width: "16px", height: "16px" }} />
            <strong>{t("pg.deleteQuestion")}</strong>
          </div>
          <div className="actions" style={{ marginTop: "8px" }}>
            <button className="danger" disabled={loading} onClick={() => void removeConversation()}>
              <Trash2 style={{ width: "13px", height: "13px" }} />
              {t("pg.deleteConfirm")}
            </button>
            <button disabled={loading} onClick={() => setConfirmDelete(false)}>{t("pg.keep")}</button>
          </div>
        </div>
      )}

      <div className="actions rc-playground-tools">
        <label>
          {t("pg.tempModel")}
          <select value={model} onChange={(e) => setModel(e.target.value)}>
            <option value="">{t("pg.savedModel")}</option>
            {demoModels.map((m) => (
              <option key={m}>{m}</option>
            ))}
          </select>
        </label>
        <button onClick={() => setCompare(!compare)} className={compare ? "rc-active" : ""}>
          <GitCompare size={14} />
          {compare ? t("pg.hideCompare") : t("pg.compare")}
        </button>
      </div>
      {compare && <Compare chatbotId={chatbotId} />}
      <div className="chat-history" role="log" aria-label={t("pg.chatMessages")} aria-live="polite">
        {loading && (
          <p role="status" className="muted rc-loading-row" style={{ justifyContent: "center" }}>
            <RefreshCw style={{ width: "16px", height: "16px", animation: "spin 1s linear infinite" }} />
            {t("pg.loadingConversation")}
          </p>
        )}

        {!loading && !messages.length && !pending && (
          <div className="chat-empty">
            <div className="empty-icon-wrap">
              <Bot style={{ width: "32px", height: "32px" }} />
            </div>
            <h3>{t("pg.empty.title")}</h3>
            <p>
              {t("pg.empty.text1")}
              <br />
              {t("pg.empty.text2")}
            </p>
          </div>
        )}

        {messages.map((message) => (
          <article className={`chat-message ${message.role.toLowerCase()}`} key={message.id}>
            <div className="rc-inline" style={{ marginBottom: "6px" }}>
              {message.role === "USER" ? (
                <User style={{ width: "13px", height: "13px", color: "#99f6e4" }} />
              ) : (
                <Bot style={{ width: "13px", height: "13px", color: "#0d9488" }} />
              )}
              <span className="message-role">{message.role === "USER" ? t("pg.you") : name}</span>
            </div>
            <div className="message-content">{message.content}</div>
            <SourceCards sources={message.sources} chatbotId={chatbotId} />
          </article>
        ))}

        {pending && (
          <>
            <article className="chat-message user">
              <div className="rc-inline" style={{ marginBottom: "6px" }}>
                <User style={{ width: "13px", height: "13px", color: "#99f6e4" }} />
                <span className="message-role">{t("pg.you")}</span>
              </div>
              <div className="message-content">{pending}</div>
            </article>
            <div className="generation-status" role="status">
              <Loader2 style={{ width: "16px", height: "16px", animation: "spin 1s linear infinite" }} />
              {t("pg.preparing")}
            </div>
          </>
        )}

        <div ref={bottom} />
      </div>

      {error && (
        <p className="error" role="alert" style={{ margin: "12px 0" }}>
          <AlertCircle style={{ width: "16px", height: "16px", flexShrink: 0 }} />
          {t(error)}
        </p>
      )}

      {!messages.length && (
        <div className="actions rc-starters">
          {starterQuestions.map((q) => (
            <button key={q} type="button" className="rc-chip" disabled={!!pending} onClick={() => setQuestion(q)}>{q}</button>
          ))}
        </div>
      )}
      <form className="question-form" onSubmit={send}>
        <label className="sr-only" htmlFor="question">{t("pg.yourQuestion")}</label>
        <textarea
          id="question"
          rows={2}
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={t("pg.placeholder")}
          required
          maxLength={2000}
          disabled={!!pending || loading}
        />
        <button className="primary" type="submit" disabled={!!pending || loading || !question.trim()} style={{ height: "48px", padding: "0 20px" }}>
          {pending ? (
            <>
              <Loader2 style={{ width: "16px", height: "16px", animation: "spin 1s linear infinite" }} />
              {t("pg.thinking")}
            </>
          ) : (
            <>
              {t("pg.send")}
              <Send style={{ width: "15px", height: "15px" }} />
            </>
          )}
        </button>
      </form>

      <p className="chat-disclaimer">{t("pg.disclaimer")}</p>
    </section>
  );
}
