"use client";
import { useEffect, useState, useRef, type FormEvent } from "react";
import {
  publicChatService,
  type PublicBot,
} from "@/services/public-chat.service";
import { errorMessage } from "@/services/api";
import type { ChatMessage } from "@/types/playground";
import { confirmDialog } from "@/components/confirm-dialog";
import { LanguageSwitcher } from "@/components/language-switcher";
import { useT } from "@/i18n/context";
import {
  Bot,
  Send,
  Sparkles,
  FileText,
  RotateCcw,
  Copy,
  Check,
  ChevronDown,
  ShieldCheck,
  MessageSquare,
} from "lucide-react";

export function PublicChat({
  kind,
  token,
  botSlug,
}: {
  kind: "share" | "widget";
  token: string;
  botSlug?: string;
}) {
  const t = useT();
  const [password,setPassword] = useState("");
  const [unlocked,setUnlocked] = useState(false);
  const [bot, setBot] = useState<PublicBot | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState("");
  const [session, setSession] = useState<string>();
  const [conversation, setConversation] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let active = true;
    publicChatService
      .metadata(kind, token, botSlug)
      .then((b) => {
        if (active) setBot(b);
      })
      .catch((e) => {
        if (active) setError(errorMessage(e));
      });
    return () => {
      active = false;
    };
  }, [kind, token, botSlug]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, busy]);

  async function handleReset() {
    if (!(await confirmDialog({ message: t("pub.resetConfirm"), confirmLabel: t("pub.newChat"), danger: false }))) return;
    setMessages([]);
    setQuestion("");
    setSession(undefined);
    setConversation(undefined);
    setError("");
    inputRef.current?.focus();
  }

  async function handleCopy(text: string, id: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      // ignore
    }
  }

  async function sendText(textToSend: string) {
    if (!textToSend.trim() || busy) return;
    const text = textToSend.trim();
    setBusy(true);
    setError("");
    try {
      const answer = await publicChatService.ask(
        kind,
        token,
        text,
        conversation,
        session,
        password,
      );
      setSession(answer.session_token);
      setConversation(answer.conversation_id);
      setMessages((m) => [
        ...m,
        {
          id: answer.user_message_id,
          role: "USER",
          content: text,
          sources: [],
        },
        {
          id: answer.message_id,
          role: "ASSISTANT",
          content: answer.answer,
          sources: answer.sources || [],
        },
      ]);
      setQuestion("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    void sendText(question);
  }

  if(bot?.passwordRequired && !unlocked) return <main className="page-content" style={{maxWidth:560,margin:'60px auto'}}><section className="panel"><div className="login-card-top"><h1>{t("pub.passwordRequired")}</h1><LanguageSwitcher compact /></div><p className="muted">{t("pub.passwordNote")}</p><form className="form-stack" onSubmit={async e=>{e.preventDefault();setBusy(true);setError('');try{await publicChatService.verify(kind,token,password);setUnlocked(true);}catch(e){setError(errorMessage(e));}finally{setBusy(false);}}}><label>{t("pub.linkPassword")}<input type="password" value={password} onChange={e=>setPassword(e.target.value)} required/></label>{error && <p role="alert" className="error">{t(error)}</p>}<button className="primary" disabled={busy}>{busy?t("pub.checking"):t("pub.openChat")}</button></form></section></main>;
  const isWidget = kind === "widget";

  return (
    <div className={`public-chat-viewport ${isWidget ? "widget-mode" : "page-mode"}`} style={{"--public-primary":bot?.color || "#0d9488"} as React.CSSProperties}>
      <main className="public-chat-shell">
        {/* Top Header */}
        <header className="public-chat-nav">
          <div className="public-chat-brand">
            <div className="public-bot-avatar">
              <Bot style={{ width: "22px", height: "22px" }} />
              <span className="public-live-indicator" />{bot?.avatar && <span>{bot.avatar}</span>}
            </div>
            <div className="public-bot-info">
              <div className="public-bot-title-row">
                <h1>{bot?.name || t("pub.defaultName")}</h1>
                <span className="public-verified-pill">
                  <ShieldCheck style={{ width: "12px", height: "12px" }} />
                  {t("pub.demoChat")}
                </span>
              </div>
              <p className="public-bot-subtitle">
                {bot?.description || t("pub.poweredBy")}
              </p>
            </div>
          </div>

          <div className="public-header-actions">
            {messages.length > 0 && (
              <button
                type="button"
                className="public-reset-btn"
                onClick={() => void handleReset()}
                title={t("pub.newChatTitle")}
              >
                <RotateCcw style={{ width: "13px", height: "13px" }} />
                <span>{t("pub.newChat")}</span>
              </button>
            )}
          </div>
        </header>

        {/* Messages Body */}
        <div className="public-chat-stream" aria-live="polite">
          {!bot && !error && (
            <div className="public-chat-loading">
              <div className="public-spinner" />
              <p>{t("pub.connecting")}</p>
            </div>
          )}

          {bot && !messages.length && (
            <div className="public-welcome-hero">
              <div className="welcome-avatar-wrap">
                <div className="welcome-avatar-inner">
                  <Sparkles style={{ width: "28px", height: "28px" }} />
                </div>
              </div>
              <h2>{bot.welcome || t("pub.welcomeDefault")}</h2>
              <p className="welcome-desc">{t("pub.explore", { name: bot.name })}</p>

              {bot.starter_questions && bot.starter_questions.length > 0 && (
                <div className="public-starter-box">
                  <p className="starter-label">
                    <MessageSquare style={{ width: "13px", height: "13px" }} />
                    {t("pub.suggested")}
                  </p>
                  <div className="public-starter-grid">
                    {bot.starter_questions.map((q, idx) => (
                      <button
                        key={idx}
                        className="public-starter-chip"
                        onClick={() => void sendText(q)}
                        disabled={busy}
                      >
                        <Sparkles style={{ width: "12px", height: "12px", flexShrink: 0 }} />
                        <span>{q}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Conversation History */}
          {messages.map((m) => {
            const isUser = m.role === "USER";
            const isCopied = copiedId === m.id;

            return (
              <article
                key={m.id}
                className={`public-bubble-wrap ${isUser ? "user-bubble-wrap" : "bot-bubble-wrap"}`}
              >
                <div className="public-bubble-meta">
                  <span className="bubble-sender-name">
                    {isUser ? t("pub.you") : bot?.name || t("pub.assistant")}
                  </span>
                  {!isUser && (
                    <button
                      className="copy-bubble-btn"
                      onClick={() => handleCopy(m.content, m.id)}
                      title={t("pub.copyTitle")}
                    >
                      {isCopied ? (
                        <>
                          <Check style={{ width: "12px", height: "12px", color: "#10b981" }} />
                          <span className="text-emerald-600">{t("pub.copied")}</span>
                        </>
                      ) : (
                        <>
                          <Copy style={{ width: "12px", height: "12px" }} />
                          <span>{t("pub.copy")}</span>
                        </>
                      )}
                    </button>
                  )}
                </div>

                <div className={`public-bubble ${isUser ? "user-style" : "bot-style"}`}>
                  <p className="bubble-text">{m.content}</p>

                  {/* Grounded Sources Accordion */}
                  {m.sources && m.sources.length > 0 && (
                    <div className="public-sources-container">
                      <p className="sources-header-tag">
                        <FileText style={{ width: "11px", height: "11px" }} />
                        {m.sources.length === 1 ? t("pub.sourceOne", { count: 1 }) : t("pub.sourceMany", { count: m.sources.length })}
                      </p>
                      <div className="public-sources-list">
                        {m.sources.map((s, i) => (
                          <details className="public-source-card" key={i}>
                            <summary className="public-source-summary">
                              <span className="source-doc-name">
                                <FileText style={{ width: "12px", height: "12px", flexShrink: 0 }} />
                                {s.document_name}
                              </span>
                              {s.page_number && (
                                <span className="source-meta-tag">{t("pub.page", { page: s.page_number })}</span>
                              )}
                              {s.sheet_name && (
                                <span className="source-meta-tag">
                                  {s.sheet_name}{s.row_number ? ` ${t("pub.row", { row: s.row_number })}` : ""}
                                </span>
                              )}
                              <ChevronDown className="source-chevron" style={{ width: "12px", height: "12px" }} />
                            </summary>
                            <blockquote className="public-source-excerpt">
                              &ldquo;{s.excerpt}&rdquo;
                            </blockquote>
                          </details>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </article>
            );
          })}

          {/* Thinking Indicator */}
          {busy && (
            <div className="public-thinking-card">
              <div className="public-bot-mini-avatar">
                <Bot style={{ width: "14px", height: "14px" }} />
              </div>
              <div className="thinking-dots">
                <span className="dot" />
                <span className="dot" />
                <span className="dot" />
              </div>
              <span className="thinking-text">{t("pub.preparing")}</span>
            </div>
          )}

          {error && (
            <div className="public-error-banner" role="alert">
              <span>{t(error)}</span>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Bottom Composer */}
        <div className="public-composer-area">
          <form className="public-composer-form" onSubmit={handleSubmit}>
            <div className="public-input-wrapper">
              <input
                ref={inputRef}
                aria-label={t("pg.yourQuestion")}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                maxLength={2000}
                placeholder={bot ? t("pub.askAnything", { name: bot.name }) : t("pub.ask")}
                disabled={!bot || busy}
                className="public-text-input"
              />
              <button
                type="submit"
                className="public-send-btn"
                disabled={!bot || busy || !question.trim()}
                aria-label={t("pub.send")}
              >
                <Send style={{ width: "16px", height: "16px" }} />
              </button>
            </div>
          </form>

          <footer className="public-footer-note">
            <span>{t("pub.footerPowered")} <strong>RAG Craft</strong> · {t("pub.footerRest")}</span>
            <LanguageSwitcher compact />
          </footer>
        </div>
      </main>
    </div>
  );
}
