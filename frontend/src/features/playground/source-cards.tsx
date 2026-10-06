"use client";
import { useState } from "react";
import type { Source } from "@/types/playground";
import { knowledgeService } from "@/services/knowledge.service";
import { errorMessage } from "@/services/api";
import { useT } from "@/i18n/context";
import { FileText, ExternalLink } from "lucide-react";

function SourceCard({ source, chatbotId }: { source: Source; chatbotId: string }) {
  const t = useT();
  const [error, setError] = useState("");
  return (
    <details className="source-card">
      <summary>
        <FileText size={13} />
        {source.document_name}
        {source.page_number && ` · ${t("src.page", { page: source.page_number })}`}
        {source.sheet_name && ` · ${t("src.row", { sheet: source.sheet_name, row: source.row_number ?? "" })}`}
      </summary>
      <blockquote>{source.excerpt}</blockquote>
      <p className="muted">{t("src.demo")}</p>
      {source.document_id && (
        <button
          className="text-button rc-inline"
          onClick={async () => {
            try {
              await knowledgeService.openFile(chatbotId, source.document_id!, source.page_number || 1);
            } catch (e) {
              setError(errorMessage(e));
            }
          }}
        >
          <ExternalLink size={13} />
          {t("src.open")}
        </button>
      )}
      {error && <p className="muted" role="status">{t(error)}</p>}
    </details>
  );
}

export function SourceCards({ sources, chatbotId }: { sources: Source[]; chatbotId: string }) {
  const t = useT();
  return sources.length ? (
    <div className="source-cards">
      <p className="source-label">{t("src.label", { count: sources.length })}</p>
      {sources.map((source, index) => (
        <SourceCard key={index} source={source} chatbotId={chatbotId} />
      ))}
    </div>
  ) : null;
}
