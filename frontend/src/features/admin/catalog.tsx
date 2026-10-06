"use client";
import { useEffect, useState, type FormEvent } from "react";
import { catalogService, type Model, type Prompt } from "@/services/catalog.service";
import { errorMessage } from "@/services/api";
import { confirmDialog } from "@/components/confirm-dialog";
import { useT } from "@/i18n/context";
import { Cpu, FileText, Pencil, Star } from "lucide-react";

export function Catalog({ kind }: { kind: "Models" | "System Prompts" }) {
  const t = useT();
  const [rows, setRows] = useState<(Model | Prompt)[]>([]);
  const [editing, setEditing] = useState<Model | Prompt | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const models = kind === "Models";

  useEffect(() => {
    let active = true;
    (models ? catalogService.models(true) : catalogService.prompts(true))
      .then((data) => { if (active) setRows(data); })
      .catch((e) => { if (active) setError(errorMessage(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [models]);

  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const enabled = data.get("enabled") === "on";
      if (editing && editing.enabled && !enabled && !(await confirmDialog({ message: t("cat.disableConfirm"), confirmLabel: t("common.disable") }))) return;
      if (models)
        await catalogService.saveModel(
          { name: String(data.get("name")), provider: "OLLAMA", model_identifier: String(data.get("identifier")), enabled, is_default: data.get("default") === "on" },
          editing?.id,
        );
      else
        await catalogService.savePrompt({ name: String(data.get("name")), prompt: String(data.get("prompt")), enabled }, editing?.id);
      setRows(await (models ? catalogService.models(true) : catalogService.prompts(true)));
      setEditing(null);
      form.reset();
      setNotice(t("cat.saved"));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="form-stack">
      <section className="panel">
        <p className="eyebrow">{t("cat.eyebrow")}</p>
        <h2>{models ? t("nav.models") : t("nav.systemPrompts")}</h2>
        <p className="muted">{models ? t("cat.modelsText") : t("cat.promptsText")}</p>
        {loading ? (
          <p role="status">{t("common.loading")}</p>
        ) : !rows.length ? (
          <p className="empty-state">{t("cat.empty")}</p>
        ) : (
          <div className="rc-list" style={{ marginTop: 20 }}>
            {rows.map((row) => (
              <div key={row.id} className={`rc-list-row ${editing?.id === row.id ? "selected" : ""}`}>
                <span className="rc-doc-icon">{models ? <Cpu size={16} /> : <FileText size={16} />}</span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <strong>{row.name}</strong>
                  <p className="muted rc-inline">
                    <span className={`badge ${row.enabled ? "badge-active" : "badge-inactive"}`}>{row.enabled ? t("cat.enabled") : t("cat.disabled")}</span>
                    {"is_default" in row && row.is_default && (
                      <span className="badge rc-badge-neutral"><Star size={11} />{t("cat.default")}</span>
                    )}
                  </p>
                </div>
                <button disabled={busy} onClick={() => setEditing(row)}>
                  <Pencil size={13} />
                  {t("common.edit")}
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
      <section className="panel">
        <h2>{editing ? t("cat.editItem") : models ? t("cat.addModel") : t("cat.addPrompt")}</h2>
        <form key={editing?.id || "new"} className="form-stack" onSubmit={save}>
          <label>
            {t("cat.name")}
            <input name="name" defaultValue={editing?.name} required maxLength={120} />
          </label>
          {models ? (
            <>
              <label>
                {t("cat.identifier")}
                <input name="identifier" defaultValue={editing && "model_identifier" in editing ? editing.model_identifier : ""} required maxLength={120} placeholder={t("cat.identifierPlaceholder")} />
              </label>
              <label className="checkbox-label">
                <input type="checkbox" name="default" defaultChecked={!!editing && "is_default" in editing && editing.is_default} />
                {t("cat.defaultModel")}
              </label>
            </>
          ) : (
            <label>
              {t("cat.systemPrompt")}
              <textarea name="prompt" rows={6} defaultValue={editing && "prompt" in editing ? editing.prompt : ""} required maxLength={10000} />
            </label>
          )}
          <label className="checkbox-label">
            <input type="checkbox" name="enabled" defaultChecked={editing?.enabled ?? true} />
            {t("cat.enabled")}
          </label>
          {error && <p role="alert" className="error">{t(error)}</p>}
          {notice && <p role="status" className="success-note">{notice}</p>}
          <div className="actions">
            <button disabled={busy} className="primary">{busy ? t("common.saving") : t("common.save")}</button>
            {editing && (
              <button type="button" disabled={busy} onClick={() => setEditing(null)}>{t("common.cancel")}</button>
            )}
          </div>
        </form>
      </section>
    </div>
  );
}
