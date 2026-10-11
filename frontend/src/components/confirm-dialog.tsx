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

export interface InputOptions extends ConfirmOptions {
  /** Label for the text box (for example "Reason"). */
  inputLabel: string;
  /** When set, the confirm button stays disabled until the text equals this value (retype to confirm). */
  expect?: string;
  multiline?: boolean;
  maxLength?: number;
}

interface Request extends ConfirmOptions {
  resolve: (value: boolean) => void;
  input?: Pick<InputOptions, "inputLabel" | "expect" | "multiline" | "maxLength">;
  resolveText?: (value: string | null) => void;
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

/** Like confirmDialog, but also collects text. Resolves with the text, or null when cancelled. */
export function inputDialog(options: InputOptions): Promise<string | null> {
  return new Promise((resolve) => {
    const { inputLabel, expect, multiline, maxLength, ...rest } = options;
    const detail: Request = { ...rest, input: { inputLabel, expect, multiline, maxLength }, resolve: () => undefined, resolveText: resolve };
    window.dispatchEvent(new CustomEvent<Request>(EVENT, { detail }));
  });
}

export function ConfirmHost() {
  const t = useT();
  const [request, setRequest] = useState<Request | null>(null);
  const [text, setText] = useState("");
  const confirmButton = useRef<HTMLButtonElement>(null);
  const textField = useRef<HTMLInputElement & HTMLTextAreaElement>(null);

  useEffect(() => {
    const show = (event: Event) => {
      setText("");
      setRequest((event as CustomEvent<Request>).detail);
    };
    window.addEventListener(EVENT, show);
    return () => window.removeEventListener(EVENT, show);
  }, []);

  useEffect(() => {
    if (!request) return;
    (request.input ? textField.current : confirmButton.current)?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        request.resolve(false);
        request.resolveText?.(null);
        setRequest(null);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [request]);

  function close(value: boolean) {
    request?.resolve(value);
    request?.resolveText?.(value ? text.trim() : null);
    setRequest(null);
  }

  if (!request) return null;
  const input = request.input;
  const blocked = !!input && (input.expect !== undefined ? text.trim() !== input.expect : !text.trim());
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
        {input && (
          <label className="rc-dialog-input">
            <span>{input.inputLabel}</span>
            {input.multiline ? (
              <textarea ref={textField} value={text} maxLength={input.maxLength} rows={3} onChange={(event) => setText(event.target.value)} />
            ) : (
              <input ref={textField} value={text} maxLength={input.maxLength} onChange={(event) => setText(event.target.value)} />
            )}
          </label>
        )}
        <div className="rc-dialog-actions">
          <button type="button" onClick={() => close(false)}>
            {request.cancelLabel || t("dialog.cancel")}
          </button>
          <button
            ref={confirmButton}
            type="button"
            disabled={blocked}
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
