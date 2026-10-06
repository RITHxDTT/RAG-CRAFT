"use client";
import { useEffect, useState } from "react";
import type { DocumentActivity } from "@/types/document";
import { knowledgeService } from "@/services/knowledge.service";
import { errorMessage } from "@/services/api";
import { StatusBadge } from "@/components/status-badge";
import { useLocale } from "@/i18n/context";
import { Activity, FileText, Bot, Calendar, AlertCircle, RefreshCw, ArrowRight } from "lucide-react";

export function RecentActivity({ onSelect }: { onSelect: (botId: string) => void }) {
  const { t, formatDate } = useLocale();
  const [documents, setDocuments] = useState<DocumentActivity[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    knowledgeService
      .recent()
      .then((docs) => {
        if (active) setDocuments(docs);
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
  }, []);

  return (
    <section className="recent-activity">
      <div className="section-heading">
        <h2 className="rc-inline">
          <Activity style={{ width: "18px", height: "18px", color: "#0d9488" }} />
          {t("activity.title")}
        </h2>
      </div>
      <div className="panel activity-panel">
        {loading ? (
          <p role="status" className="muted rc-loading-row" style={{ padding: "16px 0" }}>
            <RefreshCw style={{ width: "15px", height: "15px", animation: "spin 1s linear infinite" }} />
            {t("activity.loading")}
          </p>
        ) : error ? (
          <p role="alert" className="error">
            <AlertCircle style={{ width: "15px", height: "15px" }} />
            {t(error)}
          </p>
        ) : !documents.length ? (
          <div className="rc-empty-inline">
            <FileText style={{ width: "28px", height: "28px", margin: "0 auto 8px", opacity: 0.5 }} />
            <p>{t("activity.empty")}</p>
          </div>
        ) : (
          <ul>
            {documents.map((doc) => (
              <li key={doc.id}>
                <div className="rc-inline" style={{ gap: "10px" }}>
                  <div className="rc-doc-icon">
                    <FileText style={{ width: "16px", height: "16px" }} />
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <strong className="document-name">{doc.name}</strong>
                    <button className="text-button rc-activity-link" onClick={() => onSelect(doc.chatbot_id)}>
                      <Bot style={{ width: "12px", height: "12px" }} />
                      {doc.chatbot_name}
                      <ArrowRight style={{ width: "11px", height: "11px" }} />
                    </button>
                  </div>
                </div>
                <StatusBadge status={doc.status} />
                <time className="rc-inline">
                  <Calendar style={{ width: "12px", height: "12px" }} />
                  {formatDate(doc.updated_at)}
                </time>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
