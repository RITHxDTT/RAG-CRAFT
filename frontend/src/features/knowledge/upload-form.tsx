"use client";
import { useState, type FormEvent } from "react";
import type { UploadPolicy } from "@/types/document";
import { knowledgeService } from "@/services/knowledge.service";
import { errorMessage } from "@/services/api";
import { useT } from "@/i18n/context";
import { UploadCloud, X, FileText } from "lucide-react";

export function UploadForm({
  chatbotId,
  policy,
  onUploaded,
  onCancel,
}: {
  chatbotId: string;
  policy: UploadPolicy;
  onUploaded: () => void;
  onCancel: () => void;
}) {
  const t = useT();
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [completed, setCompleted] = useState(0);
  const [dragging, setDragging] = useState(false);

  async function upload(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setErrors([]);
    setCompleted(0);
    const failed: File[] = [];
    const issues: string[] = [];
    for (const [index, file] of files.entries()) {
      try {
        const extension = file.name.split(".").pop()?.toLowerCase();
        if (!extension || !policy.extensions.map((e) => e.toLowerCase()).includes(extension)) throw new Error(t("upload.unsupported"));
        if (!file.size) throw new Error(t("upload.empty"));
        if (file.size > policy.max_upload_size_mb * 1024 * 1024) throw new Error(t("upload.tooLarge", { size: policy.max_upload_size_mb }));
        await knowledgeService.upload(chatbotId, file);
      } catch (e) {
        failed.push(file);
        issues.push(`${file.name}: ${t(errorMessage(e))}`);
      }
      setCompleted(index + 1);
    }
    setBusy(false);
    setErrors(issues);
    setFiles(failed);
    if (!failed.length) onUploaded();
  }

  const buttonLabel = busy
    ? t("upload.uploading")
    : files.length === 0
      ? t("upload.buttonEmpty")
      : files.length === 1
        ? t("upload.buttonOne")
        : t("upload.buttonMany", { count: files.length });

  return (
    <section className="panel upload-panel">
      <div className="section-heading">
        <h2>{t("upload.title")}</h2>
        <button className="text-button" disabled={busy} onClick={onCancel}>
          <X size={16} />
          {t("common.close")}
        </button>
      </div>
      <p className="muted">{t("upload.text")}</p>
      <form className="form-stack" onSubmit={upload}>
        <label
          className={`file-picker ${dragging ? "dragging" : ""}`}
          onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragging(false);
            if (!busy) setFiles(Array.from(e.dataTransfer.files));
          }}
        >
          <UploadCloud size={36} color="#0d9488" />
          <span>{t("upload.drop")}</span>
          <input
            type="file"
            multiple
            disabled={busy}
            accept={policy.extensions.map((e) => "." + e).join(",")}
            onChange={(e) => setFiles(Array.from(e.target.files || []))}
          />
          <span>{t("upload.limits", { types: policy.extensions.join(", ").toUpperCase(), size: policy.max_upload_size_mb })}</span>
        </label>
        {files.map((file, index) => (
          <div className="rc-file-row" key={`${file.name}-${index}`}>
            <FileText size={15} />
            <span>
              {file.name} · {(file.size / 1024).toFixed(1)} KB
            </span>
            <button type="button" className="text-button" disabled={busy} aria-label={t("upload.remove", { name: file.name })} onClick={() => setFiles(files.filter((_, i) => i !== index))}>
              <X size={14} />
            </button>
          </div>
        ))}
        {busy && (
          <div className="rc-progress">
            <p role="status">{t("upload.progress", { done: completed, total: files.length })}</p>
            <div className="rc-progress-track"><div style={{ width: `${files.length ? (completed / files.length) * 100 : 0}%` }} /></div>
          </div>
        )}
        {errors.map((error, index) => (
          <p className="error" role="alert" key={index}>{error}</p>
        ))}
        <button disabled={busy || !files.length} className="primary">{buttonLabel}</button>
      </form>
    </section>
  );
}
