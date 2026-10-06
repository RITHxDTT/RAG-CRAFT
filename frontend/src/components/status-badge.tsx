"use client";
import { Clock, Loader2, AlertCircle } from "lucide-react";
import { useLabel } from "@/i18n/context";

export function StatusBadge({ status }: { status: string }) {
  const labelFor = useLabel();
  const norm = status.toUpperCase();

  const renderIndicator = () => {
    if (["PROCESSING", "UPLOADING", "CHUNKING", "INDEXING", "CRAWLING", "EXTRACTING"].includes(norm)) {
      return <Loader2 style={{ width: "12px", height: "12px", animation: "spin 1s linear infinite" }} aria-hidden="true" />;
    }
    if (norm === "QUEUED") {
      return <Clock style={{ width: "11px", height: "11px" }} aria-hidden="true" />;
    }
    if (norm === "FAILED" || norm === "ERROR") {
      return <AlertCircle style={{ width: "11px", height: "11px" }} aria-hidden="true" />;
    }
    return <span className="badge-dot" aria-hidden="true" />;
  };

  return (
    <span className={`badge badge-${status.toLowerCase()}`}>
      {renderIndicator()}
      {labelFor(status)}
    </span>
  );
}
