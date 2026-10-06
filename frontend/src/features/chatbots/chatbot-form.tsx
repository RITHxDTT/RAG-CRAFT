"use client";
import { useEffect, useState, type FormEvent } from "react";
import { catalogService, type Model, type Prompt } from "@/services/catalog.service";
import { chatbotService } from "@/services/chatbot.service";
import { errorMessage } from "@/services/api";
import type { Chatbot } from "@/types/chatbot";
import { confirmDialog } from "@/components/confirm-dialog";
import { useT } from "@/i18n/context";
import { Bot, Sliders, Cpu, Check, X, Loader2, AlertCircle } from "lucide-react";

const TONES = ["PROFESSIONAL", "FRIENDLY", "CONCISE", "EDUCATIONAL", "DETAILED"] as const;

export function ChatbotForm({
  bot,
  onSaved,
  onCancel,
}: {
  bot?: Chatbot;
  onSaved: (bot: Chatbot) => void;
  onCancel: () => void;
}) {
  const t = useT();
  const [error, setError] = useState("");
  const [models, setModels] = useState<Model[]>([]);
  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    Promise.all([catalogService.models(), catalogService.prompts()])
      .then(([models, prompts]) => {
        if (active) {
          setModels(models);
          setPrompts(prompts);
        }
      })
      .catch((e) => {
        if (active) setError(errorMessage(e));
      })
      .finally(() => {
        if (active) setCatalogLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    if (bot?.status === "ACTIVE" && values.get("status") === "INACTIVE" && !(await confirmDialog({ message: t("form.confirmDisable"), confirmLabel: t("common.disable") })))
      return;
    setBusy(true);
    setError("");
    const data = {
      starter_questions: String(values.get("starters") || "")
        .split("\n")
        .map((s) => s.trim())
        .filter(Boolean),
      name: String(values.get("name")).trim(),
      description: String(values.get("description")).trim(),
      avatar: String(values.get("avatar") || ""),
    };
    try {
      const settings = {
        welcome_message: String(values.get("welcome") || "Hello! How can I help?"),
        fallback_message: String(values.get("fallback") || "I do not have that information."),
        show_citations: values.get("citations") === "on",
        system_instruction: String(values.get("instruction") || bot?.settings.system_instruction || "Answer using the provided knowledge."),
        model_name: models.find((m) => m.id === values.get("model"))?.model_identifier || "",
        model_id: String(values.get("model")),
        prompt_template_id: String(values.get("prompt") || "") || null,
        custom_instruction: String(values.get("custom_instruction") || ""),
        tone: String(values.get("tone")) as NonNullable<Chatbot["settings"]["tone"]>,
        top_k: Number(values.get("top_k") || 5),
        temperature: Number(values.get("temperature") || 0.2),
        answer_length: String(values.get("answer_length") || "MEDIUM") as Chatbot["settings"]["answer_length"],
      };
      const result = bot
        ? await chatbotService.update(bot.id, { ...data, settings, status: String(values.get("status")) as Chatbot["status"] })
        : await chatbotService.create({ ...data, settings });
      onSaved(result);
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel" style={{ maxWidth: "800px" }}>
      <div className="section-heading" style={{ marginBottom: "8px" }}>
        <h2>{bot ? t("form.editTitle") : t("form.createTitle")}</h2>
      </div>
      <p className="muted" style={{ marginBottom: "24px" }}>{bot ? t("form.editText") : t("form.createText")}</p>

      <form onSubmit={submit} className="form-stack">
        <label>
          <span>{t("form.name")}</span>
          <input name="name" placeholder={t("form.namePlaceholder")} defaultValue={bot?.name} required maxLength={120} />
        </label>

        <label>
          <span>{t("form.description")}</span>
          <textarea name="description" placeholder={t("form.descriptionPlaceholder")} defaultValue={bot?.description} maxLength={2000} rows={3} />
        </label>

        <label>
          {t("form.avatar")}
          <input name="avatar" defaultValue={bot?.avatar} placeholder="🤖" maxLength={8} />
        </label>
        <label>
          {t("form.welcome")}
          <textarea name="welcome" defaultValue={bot?.settings.welcome_message || "Hello! How can I help?"} maxLength={2000} />
        </label>
        <label>
          {t("form.fallback")}
          <textarea name="fallback" defaultValue={bot?.settings.fallback_message || "I do not have that information."} maxLength={2000} />
        </label>
        <label className="checkbox-label">
          <input type="checkbox" name="citations" defaultChecked={bot?.settings.show_citations !== false} />
          {t("form.citations")}
        </label>
        <label>
          {t("form.starters")}
          <span className="muted rc-hint">{t("form.startersHint")}</span>
          <textarea name="starters" rows={4} defaultValue={bot?.starter_questions?.join("\n")} maxLength={3010} />
        </label>
        {catalogLoading ? (
          <p role="status">{t("form.loadingConfig")}</p>
        ) : (
          <>
            <label>
              {t("form.model")}
              <select name="model" required defaultValue={bot?.settings.model_id || models.find((m) => m.is_default)?.id || models[0]?.id}>
                <option value="">{t("form.chooseModel")}</option>
                {models.map((model) => (
                  <option key={model.id} value={model.id}>{model.name}</option>
                ))}
              </select>
            </label>
            <label>
              {t("form.promptTemplate")}
              <select name="prompt" defaultValue={bot?.settings.prompt_template_id || ""}>
                <option value="">{t("form.defaultPrompt")}</option>
                {prompts.map((prompt) => (
                  <option key={prompt.id} value={prompt.id}>{prompt.name}</option>
                ))}
              </select>
            </label>
            <label>
              {t("form.customInstruction")} <span className="muted rc-hint">{t("form.customInstructionHint")}</span>
              <textarea name="custom_instruction" rows={4} maxLength={10000} defaultValue={bot?.settings.custom_instruction} />
            </label>
            <label>
              {t("form.tone")}
              <select name="tone" defaultValue={bot?.settings.tone || "PROFESSIONAL"}>
                {TONES.map((tone) => (
                  <option key={tone} value={tone}>{t(`tone.${tone}`)}</option>
                ))}
              </select>
            </label>
          </>
        )}
        {bot && (
          <>
            <h3 className="rc-form-section">
              <Bot size={16} />
              {t("form.behavior")}
            </h3>

            <label>
              <span>{t("form.status")}</span>
              <select name="status" defaultValue={bot.status}>
                <option value="DRAFT">{t("form.status.DRAFT")}</option>
                <option value="ERROR">{t("form.status.ERROR")}</option>
                <option value="ACTIVE">{t("form.status.ACTIVE")}</option>
                <option value="INACTIVE">{t("form.status.INACTIVE")}</option>
              </select>
            </label>

            <label>
              <span>{t("form.systemInstruction")}</span>
              <textarea
                name="instruction"
                defaultValue={bot.settings.system_instruction}
                required
                maxLength={10000}
                rows={5}
                placeholder={t("form.systemInstructionPlaceholder")}
              />
            </label>

            <h3 className="rc-form-section">
              <Sliders size={16} />
              {t("form.retrieval")}
            </h3>

            <div className="form-columns">
              <label>
                <span>{t("form.topK")}</span>
                <input name="top_k" type="number" min={1} max={20} defaultValue={bot.settings.top_k} required />
              </label>
              <label>
                <span>{t("form.temperature")}</span>
                <input name="temperature" type="number" min={0} max={2} step={0.1} defaultValue={bot.settings.temperature} required />
              </label>
              <label>
                <span>{t("form.answerLength")}</span>
                <select name="answer_length" defaultValue={bot.settings.answer_length}>
                  <option value="SHORT">{t("form.length.SHORT")}</option>
                  <option value="MEDIUM">{t("form.length.MEDIUM")}</option>
                  <option value="LONG">{t("form.length.LONG")}</option>
                </select>
              </label>
            </div>

            <div className="rc-note">
              <Cpu size={15} />
              <span>
                {t("form.underlyingModel")} <strong>{bot.settings.model_name}</strong>
              </span>
            </div>
          </>
        )}

        {error && (
          <p role="alert" className="error">
            <AlertCircle style={{ width: "16px", height: "16px", flexShrink: 0 }} />
            {t(error)}
          </p>
        )}

        <div className="actions" style={{ marginTop: "12px" }}>
          <button type="submit" className="primary" disabled={busy || catalogLoading || !models.length}>
            {busy ? (
              <>
                <Loader2 style={{ width: "15px", height: "15px", animation: "spin 1s linear infinite" }} />
                {t("common.saving")}
              </>
            ) : (
              <>
                <Check style={{ width: "15px", height: "15px" }} />
                {bot ? t("form.saveChanges") : t("page.createChatbot")}
              </>
            )}
          </button>
          <button type="button" onClick={onCancel} disabled={busy}>
            <X style={{ width: "15px", height: "15px" }} />
            {t("common.cancel")}
          </button>
        </div>
      </form>
    </section>
  );
}
