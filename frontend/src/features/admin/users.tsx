"use client";
import { useEffect, useState, type FormEvent } from "react";
import { adminService, type UserRecord, type MonitoredBot } from "@/services/admin.service";
import { errorMessage } from "@/services/api";
import { Avatar } from "@/components/avatar";
import { StatusBadge } from "@/components/status-badge";
import { confirmDialog } from "@/components/confirm-dialog";
import { useLocale } from "@/i18n/context";
import { Search, Trash2, Power, Shield, Bot, X } from "lucide-react";

const FILTERS = ["ALL", "ACTIVE", "DISABLED", "ADMIN", "USER"] as const;

export function UserManagement() {
  const { t, formatDateTime } = useLocale();
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>("ALL");
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [selected, setSelected] = useState<UserRecord | null>(null);
  const [search, setSearch] = useState("");
  const [offset, setOffset] = useState(0);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    adminService
      .users()
      .then((rows) => { if (active) setUsers(rows); })
      .catch((e) => { if (active) setError(errorMessage(e)); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, []);

  async function load(next = 0) {
    setBusy(true);
    setError("");
    try {
      setUsers(await adminService.users(search, next));
      setOffset(next);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function toggle(user: UserRecord) {
    const message = user.is_active ? t("users.disableConfirm", { email: user.email }) : t("users.enableConfirm", { email: user.email });
    if (!(await confirmDialog({ message, confirmLabel: user.is_active ? t("common.disable") : t("common.enable"), danger: user.is_active }))) return;
    setBusy(true);
    setError("");
    try {
      const updated = await adminService.status(user.id, !user.is_active);
      setUsers(users.map((u) => (u.id === user.id ? updated : u)));
      if (selected?.id === user.id) setSelected(updated);
      setNotice(t("users.updated"));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function remove(user: UserRecord) {
    if (!(await confirmDialog({ message: t("users.deleteConfirm", { email: user.email }), confirmLabel: t("common.delete") }))) return;
    setBusy(true);
    try {
      await adminService.remove(user.id);
      setSelected(null);
      await load(offset);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const visible = users.filter((user) =>
    filter === "ALL" || (filter === "ACTIVE" ? user.is_active : filter === "DISABLED" ? !user.is_active : user.role === filter),
  );

  return (
    <section className="panel">
      <h2>{t("users.title")}</h2>
      <div className="actions rc-filter-row" style={{ marginTop: 12 }}>
        <form className="rc-input-icon rc-search" onSubmit={(e: FormEvent) => { e.preventDefault(); void load(); }}>
          <input aria-label={t("users.searchLabel")} value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t("users.search")} maxLength={120} />
          <Search />
        </form>
        <select aria-label={t("users.filter")} value={filter} onChange={(e) => setFilter(e.target.value as (typeof FILTERS)[number])}>
          {FILTERS.map((value) => (
            <option key={value} value={value}>{t(`users.filter.${value}`)}</option>
          ))}
        </select>
        <button disabled={busy} onClick={() => void load()}>{t("common.search")}</button>
      </div>
      {error && <p className="error" role="alert">{t(error)}</p>}
      {notice && <p className="success-note" role="status">{notice}</p>}
      {busy && <p role="status">{t("common.loading")}</p>}
      {!busy && !visible.length && <p className="empty-state">{t("users.empty")}</p>}

      <div className="rc-list">
        {visible.map((user) => (
          <div className={`rc-list-row ${selected?.id === user.id ? "selected" : ""}`} key={user.id}>
            <Avatar name={user.full_name} email={user.email} size={36} />
            <div style={{ minWidth: 0, flex: 1 }}>
              <button className="text-button" onClick={() => setSelected(user)}>{user.full_name || user.email}</button>
              <p className="muted">
                {user.email} · {t(`role.${user.role}`)} · {user.chatbot_count} {t("common.chatbots")}
              </p>
            </div>
            <span className={`badge ${user.is_active ? "badge-active" : "badge-inactive"}`}>
              {user.is_active ? t("users.active") : t("users.disabled")}
            </span>
            {user.builtIn ? (
              <span className="badge rc-badge-neutral"><Shield size={11} />{t("auth.demoAccounts")}</span>
            ) : (
              <div className="actions" style={{ marginTop: 0 }}>
                <button disabled={busy} onClick={() => void toggle(user)}>
                  <Power size={13} />
                  {user.is_active ? t("common.disable") : t("common.enable")}
                </button>
                <button disabled={busy} className="danger" onClick={() => void remove(user)}>
                  <Trash2 size={13} />
                  {t("common.delete")}
                </button>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="actions">
        <button disabled={busy || !offset} onClick={() => void load(Math.max(0, offset - 100))}>{t("common.previous")}</button>
        <button disabled={busy || users.length < 100} onClick={() => void load(offset + 100)}>{t("common.next")}</button>
      </div>

      {selected && (
        <aside className="panel rc-detail-card" style={{ marginTop: 24 }}>
          <div className="rc-inline" style={{ gap: 14 }}>
            <Avatar name={selected.full_name} email={selected.email} size={48} />
            <div>
              <h3>{selected.full_name || t("users.details")}</h3>
              <p className="muted">{selected.email}</p>
            </div>
          </div>
          <dl className="detail-grid" style={{ marginTop: 16 }}>
            <div><dt>{t("settings.role")}</dt><dd>{t(`role.${selected.role}`)}</dd></div>
            <div><dt>{t("nav.chatbots")}</dt><dd>{selected.chatbot_count}</dd></div>
            <div><dt>{t("kb.col.status")}</dt><dd>{selected.is_active ? t("users.active") : t("users.disabled")}</dd></div>
            <div><dt>{t("users.joined", { date: "" }).trim()}</dt><dd>{formatDateTime(selected.created_at)}</dd></div>
          </dl>
          <button className="text-button rc-inline" style={{ marginTop: 12 }} onClick={() => setSelected(null)}>
            <X size={14} />
            {t("users.closeDetails")}
          </button>
        </aside>
      )}
    </section>
  );
}

export function Monitoring({ onOpen }: { onOpen: (id: string) => void }) {
  const { t, formatDate } = useLocale();
  const [rows, setRows] = useState<MonitoredBot[]>([]);
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    let active = true;
    adminService
      .bots()
      .then((data) => { if (active) setRows(data); })
      .catch((e) => { if (active) setError(errorMessage(e)); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, []);

  async function load(next: number) {
    setBusy(true);
    try {
      setRows(await adminService.bots(next));
      setOffset(next);
      setError("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel">
      <h2>{t("mon.title")}</h2>
      {busy && <p role="status">{t("common.loading")}</p>}
      {error && <p className="error" role="alert">{t(error)}</p>}
      {!busy && !rows.length && <p className="empty-state">{t("mon.empty")}</p>}
      <div className="rc-list">
        {rows.map((bot) => (
          <div key={bot.id} className="rc-list-row">
            <span className="rc-doc-icon"><Bot size={16} /></span>
            <div style={{ minWidth: 0, flex: 1 }}>
              <button className="text-button" onClick={() => onOpen(bot.id)}>{bot.name}</button>
              <p className="muted">
                {bot.owner_name} · {bot.owner_email} · {bot.knowledge_count} {t("common.sources")} · {bot.channel_count} {t("common.channels")} · {formatDate(bot.created_at)}
              </p>
            </div>
            <StatusBadge status={bot.status} />
          </div>
        ))}
      </div>
      <div className="actions">
        <button disabled={busy || !offset} onClick={() => void load(Math.max(0, offset - 100))}>{t("common.previous")}</button>
        <button disabled={busy || rows.length < 100} onClick={() => void load(offset + 100)}>{t("common.next")}</button>
      </div>
    </section>
  );
}
