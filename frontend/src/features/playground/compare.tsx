"use client";
import { useState } from "react";
import { playgroundService } from "@/services/playground.service";
import { errorMessage } from "@/services/api";
import type { Answer } from "@/types/playground";
import { SourceCards } from "./source-cards";
import { useT } from "@/i18n/context";

export const demoModels = ["Llama 3.2 3B", "Qwen", "Custom Model"];

export function Compare({ chatbotId }: { chatbotId: string }) {
  const t = useT();
  const [question, setQuestion] = useState("");
  const [a, setA] = useState(demoModels[0]);
  const [b, setB] = useState(demoModels[1]);
  const [answers, setAnswers] = useState<Answer[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  return (
    <section className="panel rc-compare">
      <h3>{t("cmp.title")}</h3>
      <p className="muted">{t("cmp.text")}</p>
      <form
        className="form-stack"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError("");
          try {
            setAnswers(await playgroundService.compare(chatbotId, question, [a, b]));
          } catch (e) {
            setError(errorMessage(e));
          } finally {
            setBusy(false);
          }
        }}
      >
        <div className="form-columns two">
          <label>
            {t("cmp.modelA")}
            <select value={a} onChange={(e) => setA(e.target.value)}>
              {demoModels.map((m) => <option key={m}>{m}</option>)}
            </select>
          </label>
          <label>
            {t("cmp.modelB")}
            <select value={b} onChange={(e) => setB(e.target.value)}>
              {demoModels.map((m) => <option key={m}>{m}</option>)}
            </select>
          </label>
        </div>
        <label>
          {t("cmp.question")}
          <input value={question} onChange={(e) => setQuestion(e.target.value)} required maxLength={2000} />
        </label>
        <div className="actions">
          <button className="primary" disabled={busy}>{busy ? t("cmp.comparing") : t("cmp.button")}</button>
        </div>
      </form>
      {error && <p className="error" role="alert">{t(error)}</p>}
      <div className="compare-grid">
        {answers.map((answer, index) => (
          <article className="panel rc-compare-card" key={index}>
            <h4>
              <span className="rc-compare-tag">{index === 0 ? "A" : "B"}</span>
              {t("cmp.model", { label: index === 0 ? a : b })}
            </h4>
            <p style={{ whiteSpace: "pre-wrap" }}>{answer.answer}</p>
            <SourceCards chatbotId={chatbotId} sources={answer.sources} />
          </article>
        ))}
      </div>
    </section>
  );
}
