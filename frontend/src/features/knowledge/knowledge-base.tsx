"use client";
import { useCallback, useEffect, useState, useRef } from "react";
import { knowledgeService } from "@/services/knowledge.service";
import { errorMessage } from "@/services/api";
import type { DocumentDetail as Detail, KnowledgeDocument, UploadPolicy } from "@/types/document";
import { StatusBadge } from "@/components/status-badge";
import { UploadForm } from "./upload-form";
import { DocumentDetail } from "./document-detail";
import { useLocale } from "@/i18n/context";
import {
  FileText,
  UploadCloud,
  RefreshCw,
  Eye,
  RotateCcw,
  Trash2,
  AlertTriangle,
  CheckCircle2,
  Info,
  FileCode,
  File,
  Plus,
  Globe,
  FileSpreadsheet,
} from "lucide-react";

export function KnowledgeBase({ chatbotId, onChanged }: { chatbotId: string; onChanged: () => void }) {
  const { t, formatDate } = useLocale();
  const previousStatuses = useRef("");
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [policy, setPolicy] = useState<UploadPolicy | null>(null);
  const [loading, setLoading] = useState(true);
  const [website, setWebsite] = useState(false);
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [detail, setDetail] = useState<Detail | null>(null);
  const [deleting, setDeleting] = useState<KnowledgeDocument | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [docs, config] = await Promise.all([knowledgeService.list(chatbotId), knowledgeService.policy()]);
      const signature = docs.map((doc) => `${doc.id}:${doc.status}`).join("|");
      const changed = previousStatuses.current !== signature;
      previousStatuses.current = signature;
      setDocuments(docs);
      setDetail((current) => (current ? docs.find((doc) => doc.id === current.id) || null : null));
      setPolicy(config);
      if (changed) onChanged();
      setError("");
    } catch (error) {
      setError(errorMessage(error));
    }
  }, [chatbotId, onChanged]);

  useEffect(() => {
    let active = true;
    Promise.all([knowledgeService.list(chatbotId), knowledgeService.policy()])
      .then(([docs, config]) => {
        if (active) {
          setDocuments(docs);
          setPolicy(config);
        }
      })
      .catch((error) => {
        if (active) setError(errorMessage(error));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [chatbotId]);

  useEffect(() => {
    if (!policy?.processing_enabled || !documents.some((doc) => !["READY", "FAILED", "REVIEW"].includes(doc.status))) return;
    const timer = setInterval(() => {
      void refresh();
    }, 650);
    return () => clearInterval(timer);
  }, [documents, policy, refresh]);

  async function action(kind: "delete" | "retry" | "reindex", doc: KnowledgeDocument) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await knowledgeService[kind](chatbotId, doc.id);
      setDeleting(null);
      setDetail(null);
      await refresh();
      onChanged();
      setNotice(kind === "delete" ? t("kb.notice.deleted") : t("kb.notice.queued"));
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function view(doc: KnowledgeDocument) {
    setBusy(true);
    setError("");
    try {
      setDetail(await knowledgeService.detail(chatbotId, doc.id));
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  function uploaded() {
    setAdding(false);
    setNotice(t("kb.notice.uploaded"));
    void refresh();
    onChanged();
  }

  const fileIcon = (fileType: string) => {
    const ext = fileType.toLowerCase();
    const size = { width: "16px", height: "16px" };
    if (ext.includes("pdf") || ext.includes("doc")) return <FileText style={size} />;
    if (ext.includes("xls")) return <FileSpreadsheet style={size} />;
    if (ext.includes("web")) return <Globe style={size} />;
    if (ext.includes("md") || ext.includes("json") || ext.includes("txt")) return <FileCode style={size} />;
    return <File style={size} />;
  };

  return (
    <>
      <div className="section-heading">
        <div>
          <h2>{t("kb.title")}</h2>
          <p className="muted section-subtitle">{t("kb.subtitle")}</p>
        </div>
        <div className="actions">
          <button onClick={() => setWebsite(!website)}>
            <Globe size={14} />
            {t("kb.addWebsite")}
          </button>
          <button disabled={busy} onClick={() => void refresh()}>
            <RefreshCw style={{ width: "14px", height: "14px", animation: busy ? "spin 1s linear infinite" : "none" }} />
            {t("common.refresh")}
          </button>
          <button className="primary" disabled={!policy || adding || busy} onClick={() => setAdding(true)}>
            <Plus style={{ width: "15px", height: "15px" }} />
            {t("kb.upload")}
          </button>
        </div>
      </div>

      {policy && !policy.worker_running && (
        <div className="info-note">
          <div className="rc-inline">
            <Info style={{ width: "16px", height: "16px", flexShrink: 0 }} />
            <strong>{t("kb.workerPaused")}</strong>
          </div>
          <span>{t("kb.workerText")}</span>
        </div>
      )}

      {error && (
        <p className="error" role="alert" style={{ marginBottom: "16px" }}>
          <AlertTriangle style={{ width: "16px", height: "16px", flexShrink: 0 }} />
          {t(error)}
        </p>
      )}

      {notice && (
        <p className="success-note" role="status">
          <CheckCircle2 style={{ width: "16px", height: "16px", flexShrink: 0 }} />
          {notice}
        </p>
      )}

      {website && (
        <section className="panel">
          <h3>{t("kb.website.title")}</h3>
          <form
            className="form-stack"
            onSubmit={async (e) => {
              e.preventDefault();
              const data = new FormData(e.currentTarget);
              setBusy(true);
              try {
                await knowledgeService.website(chatbotId, String(data.get("name")), String(data.get("url")));
                setWebsite(false);
                uploaded();
              } catch (err) {
                setError(errorMessage(err));
              } finally {
                setBusy(false);
              }
            }}
          >
            <label>
              {t("kb.website.name")}
              <input name="name" required />
            </label>
            <label>
              {t("kb.website.url")}
              <input name="url" type="url" required placeholder="https://example.com" />
            </label>
            <p className="muted">{t("kb.website.note")}</p>
            <div className="actions">
              <button className="primary" disabled={busy}>{busy ? t("kb.website.adding") : t("kb.website.add")}</button>
              <button type="button" onClick={() => setWebsite(false)}>{t("common.cancel")}</button>
            </div>
          </form>
        </section>
      )}
      {adding && policy && <UploadForm chatbotId={chatbotId} policy={policy} onUploaded={uploaded} onCancel={() => setAdding(false)} />}

      {deleting && (
        <section className="panel delete-confirm" aria-label={t("kb.delete.confirm")}>
          <h3 className="rc-inline rc-danger-text" style={{ marginBottom: "8px" }}>
            <AlertTriangle style={{ width: "18px", height: "18px" }} />
            {t("kb.delete.title", { name: deleting.name })}
          </h3>
          <p className="muted" style={{ marginBottom: "16px" }}>{t("kb.delete.text")}</p>
          <div className="actions">
            <button className="danger" disabled={busy} onClick={() => void action("delete", deleting)}>
              <Trash2 style={{ width: "14px", height: "14px" }} />
              {busy ? t("common.deleting") : t("kb.delete.confirm")}
            </button>
            <button disabled={busy} onClick={() => setDeleting(null)}>{t("kb.delete.keep")}</button>
          </div>
        </section>
      )}

      {loading ? (
        <p role="status" className="muted rc-loading-row">
          <RefreshCw style={{ width: "16px", height: "16px", animation: "spin 1s linear infinite" }} />
          {t("kb.loading")}
        </p>
      ) : documents.length ? (
        <div className="table-wrap panel">
          <table>
            <thead>
              <tr>
                <th>{t("kb.col.document")}</th>
                <th>{t("kb.col.type")}</th>
                <th>{t("kb.col.status")}</th>
                <th>{t("kb.col.uploaded")}</th>
                <th style={{ textAlign: "right" }}>{t("kb.col.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => (
                <tr key={doc.id}>
                  <td>
                    <div className="rc-inline" style={{ gap: "10px" }}>
                      {fileIcon(doc.file_type)}
                      <div>
                        <strong className="document-name">{doc.name}</strong>
                        <small>{(doc.size_bytes / 1024).toFixed(1)} KB</small>
                      </div>
                    </div>
                    {doc.error_message && <p className="error-text">{doc.error_message}</p>}
                  </td>
                  <td>
                    <span className="file-type">{doc.file_type}</span>
                  </td>
                  <td>
                    <StatusBadge status={doc.status} />
                    <small>{t("kb.chunks", { count: doc.chunkCount })}</small>
                  </td>
                  <td className="muted">{formatDate(doc.created_at)}</td>
                  <td>
                    <div className="table-actions" style={{ justifyContent: "flex-end" }}>
                      <button disabled={busy} onClick={() => void view(doc)} title={t("kb.viewTitle")}>
                        <Eye style={{ width: "13px", height: "13px" }} />
                        {t("kb.view")}
                      </button>
                      <label className="replace-file">
                        {t("kb.replace")}
                        <input
                          type="file"
                          accept=".pdf,.docx,.txt,.md,.xlsx"
                          disabled={busy}
                          onChange={async (e) => {
                            const file = e.target.files?.[0];
                            if (!file) return;
                            setBusy(true);
                            try {
                              await knowledgeService.replace(chatbotId, doc.id, file);
                              await refresh();
                              onChanged();
                            } catch (err) {
                              setError(errorMessage(err));
                            } finally {
                              setBusy(false);
                            }
                          }}
                        />
                      </label>
                      {doc.status === "FAILED" && (
                        <button disabled={busy} onClick={() => void action("retry", doc)} title={t("kb.retryTitle")}>
                          <RotateCcw style={{ width: "13px", height: "13px" }} />
                          {t("kb.retry")}
                        </button>
                      )}
                      {doc.status === "READY" && (
                        <button disabled={busy} onClick={() => void action("reindex", doc)} title={t("kb.reprocessTitle")}>
                          <RefreshCw style={{ width: "13px", height: "13px" }} />
                          {t("kb.reprocess")}
                        </button>
                      )}
                      <button className="danger" disabled={busy || doc.status === "PROCESSING"} onClick={() => setDeleting(doc)} title={t("kb.deleteTitle")}>
                        <Trash2 style={{ width: "13px", height: "13px" }} />
                        {t("common.delete")}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <section className="panel empty-state">
          <div className="empty-icon-wrap">
            <UploadCloud style={{ width: "32px", height: "32px" }} />
          </div>
          <h2>{t("kb.empty.title")}</h2>
          <p>{t("kb.empty.text")}</p>
          <div className="actions" style={{ justifyContent: "center" }}>
            <button className="primary" disabled={!policy || adding} onClick={() => setAdding(true)}>
              <Plus style={{ width: "16px", height: "16px" }} />
              {t("kb.empty.button")}
            </button>
            <button onClick={() => setWebsite(true)}>
              <Globe size={15} />
              {t("kb.addWebsite")}
            </button>
          </div>
        </section>
      )}

      {detail && <DocumentDetail document={detail} chatbotId={chatbotId} onClose={() => setDetail(null)} />}
    </>
  );
}
