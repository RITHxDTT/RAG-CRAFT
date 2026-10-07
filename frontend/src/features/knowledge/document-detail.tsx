"use client";
import { useState } from "react";
import { errorMessage } from "@/services/api";
import type { DocumentDetail as Detail } from "@/types/document";
import { StatusBadge } from "@/components/status-badge";
import { knowledgeService } from "@/services/knowledge.service";
import { useLocale } from "@/i18n/context";
import { FileText, X, Download, Calendar, Clock, HardDrive, FileType, History, AlertCircle } from "lucide-react";

export function DocumentDetail({ document, chatbotId, onClose }: { document: Detail; chatbotId: string; onClose: () => void }) {
  const { t, formatDateTime } = useLocale();
  const [error, setError] = useState("");
  return (
    <section className="panel" aria-label={t("doc.details")} style={{ marginTop: "24px" }}>
      <div className="section-heading">
        <div className="rc-inline" style={{ gap: "10px" }}>
          <FileText style={{ width: "20px", height: "20px" }} />
          <h2 className="document-name">{document.name}</h2>
        </div>
        <button onClick={onClose} className="rc-inline">
          <X style={{ width: "14px", height: "14px" }} />
          {t("doc.closeDetails")}
        </button>
      </div>

      <div style={{ margin: "12px 0 16px" }}>
        <StatusBadge status={document.status} />
      </div>

      <dl className="detail-grid">
        <div>
          <dt className="rc-inline"><FileType style={{ width: "12px", height: "12px" }} />{t("doc.fileType")}</dt>
          <dd>{document.file_type}</dd>
        </div>
        <div>
          <dt className="rc-inline"><HardDrive style={{ width: "12px", height: "12px" }} />{t("doc.fileSize")}</dt>
          <dd>{(document.size_bytes / 1024).toFixed(1)} KB</dd>
        </div>
        <div>
          <dt className="rc-inline"><Calendar style={{ width: "12px", height: "12px" }} />{t("doc.uploaded")}</dt>
          <dd>{formatDateTime(document.created_at)}</dd>
        </div>
        <div>
          <dt className="rc-inline"><Clock style={{ width: "12px", height: "12px" }} />{t("doc.lastUpdated")}</dt>
          <dd>{formatDateTime(document.updated_at)}</dd>
        </div>
      </dl>

      {document.error_message && (
        <p className="error" role="alert" style={{ margin: "16px 0" }}>
          <AlertCircle style={{ width: "16px", height: "16px", flexShrink: 0 }} />
          {document.error_message}
        </p>
      )}

      <div style={{ margin: "16px 0" }}>
        <button
          className="download-link"
          onClick={async () => {
            try {
              await knowledgeService.openFile(chatbotId, document.id);
            } catch (e) {
              setError(errorMessage(e));
            }
          }}
        >
          <Download style={{ width: "14px", height: "14px" }} />
          {t("doc.openOriginal")}
        </button>
        {error && <p role="alert" className="error">{t(error)}</p>}
        <p className="muted">{t("doc.chunksNote", { count: document.chunkCount })}</p>
      </div>

      <h3 className="job-heading rc-inline">
        <History style={{ width: "16px", height: "16px" }} />
        {t("doc.history")}
      </h3>
      <ul className="job-list">
        {document.jobs.map((job) => (
          <li key={job.id}>
            <StatusBadge status={job.status} />
            <span className="rc-inline muted">
              <Clock style={{ width: "12px", height: "12px" }} />
              {formatDateTime(job.created_at)}
            </span>
            {job.error_message && <span className="error-text">{job.error_message}</span>}
          </li>
        ))}
      </ul>
    </section>
  );
}
