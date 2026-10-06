"use client";
import { useEffect, useRef, useState } from "react";
import { AlertTriangle } from "lucide-react";
import { useT } from "@/i18n/context";

export interface ConfirmOptions {
  title?: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
}

interface Request extends ConfirmOptions {
  resolve: (value: boolean) => void;
}

const EVENT = "ragcraft:confirm";

/**
 * Drop-in replacement for window.confirm that renders the in-app dialog.
 * Resolves true when the person confirms, false when they cancel or press Escape.
 */
export function confirmDialog(options: ConfirmOptions | string): Promise<boolean> {
  return new Promise((resolve) => {
    const detail: Request = typeof options === "string" ? { message: options, resolve } : { ...options, resolve };
    window.dispatchEvent(new CustomEvent<Request>(EVENT, { detail }));
  });
}

export function ConfirmHost() {
  const t = useT();
  const [request, setRequest] = useState<Request | null>(null);
  const confirmButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const show = (event: Event) => setRequest((event as CustomEvent<Request>).detail);
    window.addEventListener(EVENT, show);
    return () => window.removeEventListener(EVENT, show);
  }, []);

  useEffect(() => {
    if (!request) return;
    confirmButton.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        request.resolve(false);
        setRequest(null);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [request]);

  function close(value: boolean) {
    request?.resolve(value);
    setRequest(null);
  }

  if (!request) return null;
  return (
    <div className="rc-dialog-backdrop" onClick={() => close(false)} role="presentation">
      <div
        className="rc-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="rc-dialog-title"
        aria-describedby="rc-dialog-message"
        onClick={(event) => event.stopPropagation()}
      >
        <div className={`rc-dialog-icon ${request.danger === false ? "" : "danger"}`}>
          <AlertTriangle size={20} />
        </div>
        <h2 id="rc-dialog-title">{request.title || t("dialog.title")}</h2>
        <p id="rc-dialog-message">{request.message}</p>
        <div className="rc-dialog-actions">
          <button type="button" onClick={() => close(false)}>
            {request.cancelLabel || t("dialog.cancel")}
          </button>
          <button
            ref={confirmButton}
            type="button"
            className={request.danger === false ? "primary" : "danger"}
            onClick={() => close(true)}
          >
            {request.confirmLabel || t("dialog.confirm")}
          </button>
        </div>
      </div>
    </div>
  );
}
