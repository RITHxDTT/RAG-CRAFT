"use client";
import { useEffect, useState } from "react";
import { channelService, type Channel, type ChannelSettings } from "@/services/channel.service";
import { errorMessage } from "@/services/api";
import { StatusBadge } from "@/components/status-badge";
import { confirmDialog } from "@/components/confirm-dialog";
import { toast } from "@/utils/demo";
import { useLocale } from "@/i18n/context";
import { Link2, Code2, Send, Copy, ExternalLink, RefreshCw, Power, Plus, Trash2 } from "lucide-react";

function ChannelEditor({ row, onSave, busy }: { row: Channel; onSave: (settings: ChannelSettings) => Promise<void>; busy: boolean }) {
  const { t } = useLocale();
  const [settings, setSettings] = useState(row.settings);
  const [domain, setDomain] = useState("");
  const [open, setOpen] = useState(true);
  const change = (key: keyof ChannelSettings, value: string | string[]) => setSettings((previous) => ({ ...previous, [key]: value }));
  const widget = row.channel === "WEB_WIDGET";

  return (
    <form className="form-stack" onSubmit={(e) => { e.preventDefault(); void onSave(settings); }}>
      {row.channel === "PUBLIC_LINK" && (
        <>
          <label>
            {t("ch.pageTitle")}
            <input value={settings.title} onChange={(e) => change("title", e.target.value)} maxLength={120} />
          </label>
          <label>
            {t("ch.starters")}
            <textarea value={settings.starters.join("\n")} onChange={(e) => change("starters", e.target.value.split("\n"))} />
          </label>
          <div className="form-columns two">
            <label>
              {t("ch.password")}
              <input type="password" value={settings.password} onChange={(e) => change("password", e.target.value)} autoComplete="new-password" />
            </label>
            <label>
              {t("ch.expires")}
              <input type="datetime-local" value={settings.expires} onChange={(e) => change("expires", e.target.value)} />
            </label>
          </div>
          <p className="muted">{t("ch.passwordNote")}</p>
        </>
      )}
      <div className="form-columns two">
        <label>
          {widget ? t("ch.primaryColor") : t("ch.themeColor")}
          <span className="rc-color-field">
            <input type="color" value={settings.color} onChange={(e) => change("color", e.target.value)} />
            <code>{settings.color}</code>
          </span>
        </label>
        {widget && (
          <label>
            {t("ch.position")}
            <select value={settings.position} onChange={(e) => change("position", e.target.value)}>
              <option value="BOTTOM_RIGHT">{t("ch.bottomRight")}</option>
              <option value="BOTTOM_LEFT">{t("ch.bottomLeft")}</option>
            </select>
          </label>
        )}
      </div>
      <label>
        {t("ch.welcome")}
        <textarea value={settings.welcome} onChange={(e) => change("welcome", e.target.value)} maxLength={2000} />
      </label>
      {widget && (
        <>
          <label>
            {t("ch.icon")}
            <input value={settings.icon} onChange={(e) => change("icon", e.target.value)} maxLength={8} style={{ maxWidth: 120 }} />
          </label>
          <h3 className="rc-form-section">{t("ch.domains")}</h3>
          <div className="actions">
            <input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="example.com" aria-label={t("ch.domainLabel")} style={{ maxWidth: 280 }} />
            <button
              type="button"
              onClick={() => {
                const value = domain.trim().toLowerCase();
                if (value && !settings.domains.includes(value)) change("domains", [...settings.domains, value]);
                setDomain("");
              }}
            >
              <Plus size={14} />
              {t("ch.addDomain")}
            </button>
          </div>
          {settings.domains.length ? (
            <div className="rc-chip-list">
              {settings.domains.map((value) => (
                <span className="rc-chip static" key={value}>
                  {value}
                  <button type="button" aria-label={`${t("common.remove")} ${value}`} onClick={() => change("domains", settings.domains.filter((item) => item !== value))}>
                    <Trash2 size={12} />
                  </button>
                </span>
              ))}
            </div>
          ) : (
            <p className="empty-state rc-empty-small">{t("ch.noDomains")}</p>
          )}
          <p className="muted">{t("ch.domainNote")}</p>
          <h3 className="rc-form-section">{t("ch.preview")}</h3>
          <div className={`widget-preview ${settings.position === "BOTTOM_LEFT" ? "left" : ""}`}>
            {open && (
              <div className="widget-preview-window">
                <header style={{ background: settings.color, color: "white" }}>{settings.icon} {settings.title}</header>
                <p>{settings.welcome}</p>
                <input placeholder={t("ch.previewPlaceholder")} disabled />
              </div>
            )}
            <button type="button" style={{ background: settings.color, color: "white" }} onClick={() => setOpen(!open)}>
              {settings.icon} {open ? t("ch.previewClose") : t("ch.previewChat")}
            </button>
          </div>
        </>
      )}
      <div className="actions">
        <button disabled={busy} className="primary">
          {busy ? t("common.saving") : widget ? t("ch.saveWidget") : t("ch.saveLink")}
        </button>
      </div>
    </form>
  );
}

const ICONS = { PUBLIC_LINK: Link2, WEB_WIDGET: Code2, TELEGRAM: Send } as const;
const KINDS = ["PUBLIC_LINK", "WEB_WIDGET", "TELEGRAM"] as const;

export function Channels({ chatbotId }: { chatbotId: string }) {
  const { t, formatDateTime } = useLocale();
  const [rows, setRows] = useState<Channel[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [token, setToken] = useState("");

  useEffect(() => {
    let active = true;
    channelService
      .list(chatbotId)
      .then((data) => { if (active) setRows(data); })
      .catch((e) => { if (active) setError(errorMessage(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [chatbotId]);

  async function action(fn: () => Promise<unknown>, confirmation?: { message: string; confirmLabel: string }) {
    if (confirmation && !(await confirmDialog(confirmation))) return;
    setBusy(true);
    setError("");
    try {
      await fn();
      setRows(await channelService.list(chatbotId));
      setToken("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      toast("Copied to clipboard.");
    } catch {
      setError(t("ch.copyManual"));
    }
  }

  return (
    <div className="form-stack">
      <div>
        <h2>{t("ch.title")}</h2>
        <p className="muted">{t("ch.subtitle")}</p>
      </div>
      {error && <p className="error" role="alert">{t(error)}</p>}
      {loading ? (
        <p role="status">{t("ch.loading")}</p>
      ) : (
        KINDS.map((kind) => {
          const row = rows.find((r) => r.channel === kind);
          const Icon = ICONS[kind];
          return (
            <section className="panel rc-channel" key={kind}>
              <div className="section-heading">
                <div className="rc-inline" style={{ gap: 12 }}>
                  <span className="rc-channel-icon"><Icon size={18} /></span>
                  <div>
                    <h3>{t(`ch.${kind}`)}</h3>
                    {row && (
                      <p className="muted">{t("ch.stats", { count: row.messages_this_week ?? 0, date: formatDateTime(row.updated_at) })}</p>
                    )}
                  </div>
                </div>
                <StatusBadge status={row?.status || "OFF"} />
              </div>
              {kind === "TELEGRAM" ? (
                <>
                  <ol className="rc-steps">
                    <li>{t("ch.telegram.step1")}</li>
                    <li>{t("ch.telegram.step2")}</li>
                    <li>{t("ch.telegram.step3")}</li>
                    <li>{t("ch.telegram.step4")}</li>
                  </ol>
                  <p className="muted">{t("ch.telegram.status", { status: row?.enabled ? t("ch.telegram.connected") : t("ch.telegram.disconnected") })}</p>
                  <label>
                    {t("ch.telegram.token")}
                    <input type="password" value={token} onChange={(e) => setToken(e.target.value)} placeholder={t("ch.telegram.tokenPlaceholder")} autoComplete="off" />
                  </label>
                  <div className="actions">
                    {row?.enabled ? (
                      <button
                        disabled={busy}
                        onClick={() => void action(() => channelService.toggle(chatbotId, row), { message: t("ch.telegram.disconnectConfirm"), confirmLabel: t("ch.telegram.disconnect") })}
                      >
                        <Power size={14} />
                        {t("ch.telegram.disconnect")}
                      </button>
                    ) : (
                      <button
                        className="primary"
                        disabled={busy || !token.trim()}
                        onClick={() => void action(() => (row ? channelService.connect(chatbotId, row.id, token) : channelService.create(chatbotId, "TELEGRAM", token)))}
                      >
                        <Send size={14} />
                        {busy ? t("ch.telegram.connecting") : t("ch.telegram.connect")}
                      </button>
                    )}
                  </div>
                </>
              ) : row ? (
                <>
                  <ChannelEditor key={`${row.id}-${row.updated_at}`} row={row} busy={busy} onSave={(settings) => action(() => channelService.save(chatbotId, row.id, settings))} />
                  {row.url && (
                    <label>
                      {t("ch.guestUrl")}
                      <input readOnly value={row.url} />
                    </label>
                  )}
                  {row.embed_code && (
                    <label>
                      {t("ch.embed")}
                      <textarea readOnly value={row.embed_code} rows={3} className="rc-code" />
                    </label>
                  )}
                  <div className="actions">
                    <button disabled={busy} onClick={() => void copy(row.embed_code || row.url || "")}>
                      <Copy size={14} />
                      {kind === "WEB_WIDGET" ? t("ch.copyCode") : t("ch.copyLink")}
                    </button>
                    <a className="rc-link-button" href={row.url || "#"} target="_blank" rel="noreferrer">
                      <ExternalLink size={14} />
                      {t("ch.openPreview")}
                    </a>
                    <button disabled={busy} onClick={() => void action(() => channelService.toggle(chatbotId, row))}>
                      <Power size={14} />
                      {row.enabled ? t("common.disable") : t("common.enable")}
                    </button>
                    <button
                      disabled={busy}
                      onClick={() => void action(() => channelService.regenerate(chatbotId, row.id), { message: t("ch.regenerateConfirm"), confirmLabel: t("ch.regenerate") })}
                    >
                      <RefreshCw size={14} />
                      {t("ch.regenerate")}
                    </button>
                  </div>
                </>
              ) : (
                <button className="primary" disabled={busy} onClick={() => void action(() => channelService.create(chatbotId, kind))}>
                  <Plus size={14} />
                  {busy ? t("ch.generating") : kind === "PUBLIC_LINK" ? t("ch.generateLink") : t("ch.generateWidget")}
                </button>
              )}
            </section>
          );
        })
      )}
    </div>
  );
}
