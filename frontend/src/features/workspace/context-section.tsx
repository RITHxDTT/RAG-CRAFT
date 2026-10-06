"use client";
import type { Chatbot } from "@/types/chatbot";
import { KnowledgeBase } from "@/features/knowledge/knowledge-base";
import { Playground } from "@/features/playground/playground";
import { Channels } from "@/features/channels/channels";
import { useT } from "@/i18n/context";
import { Bot } from "lucide-react";

const PAGE_KEY = { Knowledge: "nav.knowledge", Playground: "nav.playground", Channels: "nav.channels" } as const;

export function ContextSection({
  page,
  bots,
  onCreate,
  onChanged,
  selected,
  onSelect,
}: {
  page: keyof typeof PAGE_KEY;
  bots: Chatbot[];
  onCreate: () => void;
  onChanged: () => void;
  selected: string;
  onSelect: (id: string) => void;
}) {
  const t = useT();
  const bot = bots.find((b) => b.id === selected) || bots[0];
  if (!bot)
    return (
      <section className="panel empty-state">
        <div className="empty-icon-wrap"><Bot style={{ width: "32px", height: "32px" }} /></div>
        <h2>{t("context.empty.title")}</h2>
        <p>{t("context.empty.text", { page: t(PAGE_KEY[page]) })}</p>
        <button className="primary" onClick={onCreate}>{t("context.create")}</button>
      </section>
    );
  return (
    <>
      <label className="context-selector">
        {t("context.selected")}
        <select value={bot.id} onChange={(e) => onSelect(e.target.value)}>
          {bots.map((b) => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
      </label>
      {page === "Knowledge" ? (
        <KnowledgeBase key={bot.id} chatbotId={bot.id} onChanged={onChanged} />
      ) : page === "Playground" ? (
        <Playground key={bot.id} chatbotId={bot.id} name={bot.name} active starterQuestions={bot.starter_questions} />
      ) : (
        <Channels key={bot.id} chatbotId={bot.id} />
      )}
    </>
  );
}
