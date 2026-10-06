"use client";
import { useEffect, useState } from "react";
import { CheckCircle2, X } from "lucide-react";
import { useT } from "@/i18n/context";

export function Toasts() {
  const t = useT();
  const [messages, setMessages] = useState<{ id: number; text: string }[]>([]);

  useEffect(() => {
    const timers: ReturnType<typeof setTimeout>[] = [];
    let sequence = 0;
    const show = (event: Event) => {
      const entry = { id: ++sequence, text: (event as CustomEvent<string>).detail };
      setMessages((previous) => [...previous.slice(-3), entry]);
      timers.push(setTimeout(() => setMessages((previous) => previous.filter((m) => m.id !== entry.id)), 4500));
    };
    window.addEventListener("ragcraft:toast", show);
    return () => {
      window.removeEventListener("ragcraft:toast", show);
      timers.forEach(clearTimeout);
    };
  }, []);

  return (
    <div className="toast-stack" aria-live="polite">
      {messages.map((m) => (
        <div className="toast" key={m.id}>
          <CheckCircle2 size={16} aria-hidden="true" />
          <span>{t(m.text)}</span>
          <button aria-label={t("app.dismissNotification")} onClick={() => setMessages((items) => items.filter((item) => item.id !== m.id))}>
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}
