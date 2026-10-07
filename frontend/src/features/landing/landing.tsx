"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  ArrowRight,
  ArrowUpRight,
  BarChart3,
  Bot,
  Check,
  Clipboard,
  Code2,
  Database,
  FileCode,
  FileSpreadsheet,
  FileText,
  Globe,
  Layers,
  Link2,
  Menu,
  Moon,
  Quote,
  Send,
  Settings2,
  Shield,
  Sparkles,
  Sun,
  X,
} from "lucide-react";
import { authService } from "@/services/auth.service";
import type { CurrentUser } from "@/types/auth";
import { LanguageSwitcher } from "@/components/language-switcher";
import { useT } from "@/i18n/context";
import { RibbonCanvas } from "./ribbon-canvas";
import { useSiteTheme } from "./use-site-theme";

const EMBED = '<script src="https://your-domain/widget.js" data-widget-id="widget_xxx"></script>';

const SOURCES = [
  { icon: FileText, label: "PDF" },
  { icon: FileText, label: "DOCX" },
  { icon: FileCode, label: "Markdown" },
  { icon: FileCode, label: "TXT" },
  { icon: FileSpreadsheet, label: "XLSX" },
  { icon: Globe, label: "Website" },
  { icon: Link2, label: "Share link" },
  { icon: Code2, label: "Web widget" },
  { icon: Send, label: "Telegram" },
];

const FEATURES = [
  { key: "f1", icon: Bot },
  { key: "f2", icon: Database },
  { key: "f3", icon: Quote },
  { key: "f4", icon: Sparkles },
  { key: "f5", icon: Layers },
  { key: "f6", icon: BarChart3 },
  { key: "f7", icon: Settings2 },
  { key: "f8", icon: Shield },
] as const;

const STACK = ["Next.js", "TypeScript", "Tailwind CSS", "FastAPI", "PostgreSQL + pgvector", "Ollama", "Docker"];

const pill =
  "inline-flex items-center justify-center gap-2 rounded-full bg-slate-900 text-white dark:bg-white dark:text-black font-semibold shadow-glow-light dark:shadow-glow-dark transition-all duration-300 hover:scale-[1.03] active:scale-95";
const ghost =
  "inline-flex items-center justify-center gap-2 rounded-full border border-black/10 dark:border-white/10 bg-black/[0.03] dark:bg-white/[0.04] text-slate-800 dark:text-slate-300 hover:text-black dark:hover:text-white hover:bg-black/[0.06] dark:hover:bg-white/[0.08] backdrop-blur-md transition-all active:scale-95";
const card =
  "rounded-2xl bg-white dark:bg-[#0b0f17] border border-black/5 dark:border-white/10 hover:border-black/20 dark:hover:border-white/20 transition-all";
const eyebrow = "text-[11px] font-mono tracking-[0.2em] uppercase text-slate-500 dark:text-slate-400";

export function Landing() {
  const t = useT();
  const [theme, toggleTheme] = useSiteTheme();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [menu, setMenu] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let active = true;
    authService
      .me()
      .then((me) => { if (active) setUser(me); })
      .catch(() => { if (active) setUser(null); });
    return () => { active = false; };
  }, []);

  async function copyEmbed() {
    try {
      await navigator.clipboard.writeText(EMBED);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      /* clipboard unavailable */
    }
  }

  const nav = [
    { href: "#features", label: t("land.nav.features") },
    { href: "#how", label: t("land.nav.how") },
    { href: "#channels", label: t("land.nav.channels") },
    { href: "#stack", label: t("land.nav.stack") },
  ];

  return (
    <div className="landing min-h-screen bg-[#f6f7fb] text-slate-900 dark:bg-[#050608] dark:text-slate-100 antialiased overflow-x-hidden selection:bg-slate-300 selection:text-black dark:selection:bg-white dark:selection:text-black">
      <RibbonCanvas />

      {/* Ambient beam and glow */}
      <div className="fixed inset-0 pointer-events-none z-0" aria-hidden="true">
        <div className="absolute left-1/2 top-0 -translate-x-1/2 w-48 sm:w-80 h-[800px] bg-gradient-to-b from-black/[0.05] via-black/[0.02] to-transparent dark:from-white/[0.08] dark:via-white/[0.03] dark:to-transparent blur-2xl animate-beam" />
        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] rounded-full bg-slate-300/30 dark:bg-white/[0.03] blur-3xl" />
      </div>

      <div className="relative z-10 flex flex-col min-h-screen">
        {/* Header */}
        <header className="fixed top-0 left-0 w-full z-50 backdrop-blur-xl border-b border-black/5 dark:border-white/5 bg-white/70 dark:bg-[#050608]/75">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-20 flex items-center justify-between">
            <Link href="/" className="flex items-center gap-3 group">
              <span className="w-9 h-9 rounded-full border border-black/15 dark:border-white/20 bg-black/5 dark:bg-white/10 flex items-center justify-center text-[11px] font-bold tracking-tight group-hover:scale-105 transition-transform">
                RC
              </span>
              <span className="text-base font-semibold tracking-tight">RAG Craft</span>
            </Link>

            <nav className="hidden md:flex items-center gap-7 lg:gap-9 text-[13px] font-medium text-slate-600 dark:text-slate-300">
              {nav.map((item) => (
                <a key={item.href} href={item.href} className="hover:text-black dark:hover:text-white transition-colors">
                  {item.label}
                </a>
              ))}
            </nav>

            <div className="flex items-center gap-2 sm:gap-3">
              <LanguageSwitcher compact />
              <button
                type="button"
                onClick={toggleTheme}
                aria-label={t("land.nav.toggleTheme")}
                className="flex items-center justify-center w-9 h-9 rounded-full border border-black/10 dark:border-white/15 bg-black/5 dark:bg-white/5 hover:bg-black/10 dark:hover:bg-white/10 text-slate-700 dark:text-slate-200 transition-all"
              >
                {theme === "dark" ? <Sun size={15} /> : <Moon size={15} />}
              </button>
              <Link href="/app" className={`${pill} hidden sm:inline-flex text-xs px-5 py-2.5`}>
                {user ? t("land.nav.open") : t("land.nav.signIn")}
              </Link>
              <button type="button" className="md:hidden p-2 rounded-lg text-slate-700 dark:text-slate-300" aria-label={t("land.nav.menu")} onClick={() => setMenu((value) => !value)}>
                {menu ? <X size={20} /> : <Menu size={20} />}
              </button>
            </div>
          </div>
          {menu && (
            <div className="md:hidden px-6 pt-3 pb-6 border-b border-black/5 dark:border-white/5 bg-white dark:bg-[#050608] space-y-3 text-sm">
              {nav.map((item) => (
                <a key={item.href} href={item.href} onClick={() => setMenu(false)} className="block py-1.5 text-slate-600 dark:text-slate-300 hover:text-black dark:hover:text-white">
                  {item.label}
                </a>
              ))}
              <Link href="/app" className={`${pill} text-xs px-5 py-2.5 w-full`}>
                {user ? t("land.nav.open") : t("land.nav.signIn")}
              </Link>
            </div>
          )}
        </header>

        <main className="flex-grow pt-28">
          {/* Hero */}
          <section className="relative min-h-[85vh] flex flex-col items-center justify-between px-4 sm:px-6 pt-8 pb-12 overflow-hidden text-center">
            <div className="absolute inset-0 pointer-events-none" aria-hidden="true">
              <span className="absolute left-1/4 top-1/4 w-1 h-1 bg-slate-900/60 dark:bg-white rounded-full dust-particle" />
              <span className="absolute right-1/4 top-1/3 w-1.5 h-1.5 bg-slate-900/40 dark:bg-white/70 rounded-full dust-particle" style={{ animationDelay: "2s" }} />
              <span className="absolute left-1/3 top-2/3 w-1 h-1 bg-slate-900/30 dark:bg-white/50 rounded-full dust-particle" style={{ animationDelay: "4s" }} />
              <span className="absolute right-1/3 top-1/2 w-1.5 h-1.5 bg-slate-900/50 dark:bg-white/80 rounded-full dust-particle" style={{ animationDelay: "1.5s" }} />
            </div>

            <div className="max-w-4xl mx-auto flex flex-col items-center z-10">
              <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full border border-black/10 dark:border-white/10 bg-black/[0.03] dark:bg-white/[0.04] backdrop-blur-md mb-8 hover:border-black/20 dark:hover:border-white/20 transition-all">
                <Sparkles size={13} className="text-slate-600 dark:text-slate-300" />
                <span className={`${eyebrow} font-medium text-slate-700 dark:text-slate-300`}>{t("land.hero.pill")}</span>
              </div>

              <h1 className="text-4xl sm:text-6xl md:text-7xl font-extrabold tracking-tight text-slate-950 dark:text-white leading-[1.08] max-w-4xl">
                {t("land.hero.title1")}
                <br className="hidden sm:block" /> {t("land.hero.title2")}
              </h1>
              <p className="mt-6 text-sm sm:text-base md:text-lg text-slate-600 dark:text-slate-400 tracking-wide max-w-xl">{t("land.hero.sub")}</p>

              <div className="mt-8 flex flex-col sm:flex-row items-center gap-4">
                <Link href="/app" className={`${pill} px-8 py-3.5 text-xs sm:text-sm tracking-wide`}>
                  <span>{user ? t("land.nav.open") : t("land.hero.cta")}</span>
                  <ArrowUpRight size={16} />
                </Link>
                <a href="#how" className={`${ghost} px-6 py-3.5 text-xs sm:text-sm font-medium`}>
                  <Sparkles size={15} className="animate-pulse" />
                  <span>{t("land.hero.secondary")}</span>
                </a>
              </div>

              <div className="mt-16 sm:mt-20 relative flex flex-col items-center justify-center">
                <div className="absolute -top-16 w-16 sm:w-24 h-40 bg-gradient-to-t from-black/10 dark:from-white/20 via-black/[0.02] dark:via-white/5 to-transparent blur-md pointer-events-none" aria-hidden="true" />
                <div className="group relative w-20 h-20 sm:w-24 sm:h-24 rounded-[28px] p-[1.5px] bg-gradient-to-b from-black/20 via-black/5 to-transparent dark:from-white/30 dark:via-white/5 dark:to-white/0 shadow-core-light dark:shadow-core-dark transition-transform duration-500 hover:scale-110">
                  <div className="w-full h-full rounded-[26px] bg-slate-100/90 dark:bg-[#111624]/90 backdrop-blur-2xl flex items-center justify-center border border-black/10 dark:border-white/15 overflow-hidden relative">
                    <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
                    <Bot className="relative w-10 h-10 sm:w-11 sm:h-11 text-slate-900 dark:text-white drop-shadow-md" strokeWidth={1.8} />
                  </div>
                </div>
                <span className={`${eyebrow} mt-4 text-[10px]`}>{t("land.hero.node")}</span>
              </div>
            </div>

            <div className="w-full max-w-6xl mx-auto mt-16 pt-8 border-t border-black/5 dark:border-white/5 z-10">
              <p className={`${eyebrow} mb-5`}>{t("land.hero.sourcesLabel")}</p>
              <div className="flex flex-wrap items-center justify-center gap-7 sm:gap-10 opacity-65 hover:opacity-100 transition-opacity text-slate-800 dark:text-slate-300">
                {SOURCES.map(({ icon: Icon, label }) => (
                  <div key={label} className="flex items-center gap-1.5 text-xs sm:text-sm font-semibold tracking-wide">
                    <Icon size={14} /> {label}
                  </div>
                ))}
              </div>
            </div>
          </section>

          {/* Features */}
          <section id="features" className="max-w-6xl mx-auto px-4 sm:px-6 py-20 relative z-10 scroll-mt-24">
            <div className="text-center max-w-3xl mx-auto mb-12">
              <span className={eyebrow}>{t("land.features.eyebrow")}</span>
              <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight mt-2">{t("land.features.title")}</h2>
              <p className="text-slate-600 dark:text-slate-400 text-sm sm:text-base mt-3 max-w-xl mx-auto">{t("land.features.sub")}</p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {FEATURES.map(({ key, icon: Icon }) => (
                <div key={key} className={`${card} p-5`}>
                  <div className="w-9 h-9 rounded-lg bg-black/5 dark:bg-white/5 flex items-center justify-center text-slate-700 dark:text-slate-300 mb-3">
                    <Icon size={16} />
                  </div>
                  <h3 className="text-sm font-semibold">{t(`land.features.${key}.title`)}</h3>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 leading-relaxed">{t(`land.features.${key}.text`)}</p>
                </div>
              ))}
            </div>
          </section>

          {/* How it works */}
          <section id="how" className="max-w-6xl mx-auto px-4 sm:px-6 py-16 relative z-10 scroll-mt-24">
            <div className="text-center max-w-2xl mx-auto mb-12">
              <span className={eyebrow}>{t("land.how.eyebrow")}</span>
              <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight mt-2">{t("land.how.title")}</h2>
              <p className="text-slate-600 dark:text-slate-400 text-xs sm:text-sm mt-2">{t("land.how.sub")}</p>
            </div>

            <div className="rounded-3xl p-4 sm:p-6 bg-slate-200/50 dark:bg-white/[0.03] border border-black/5 dark:border-white/10 shadow-2xl backdrop-blur-2xl">
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
                {/* Pipelines */}
                <div className="lg:col-span-5 grid gap-4">
                  {(["ingest", "rag"] as const).map((flow) => (
                    <div key={flow} className="bg-white dark:bg-[#0b0f17] rounded-2xl border border-black/5 dark:border-white/10 p-5">
                      <p className={`${eyebrow} mb-3`}>{t(`land.how.${flow}`)}</p>
                      <ol className="space-y-2">
                        {[1, 2, 3, 4, 5, 6].map((step) => (
                          <li key={step} className="flex items-center gap-3 text-xs sm:text-[13px]">
                            <span className="w-6 h-6 shrink-0 rounded-full border border-black/10 dark:border-white/15 bg-black/[0.03] dark:bg-white/[0.05] font-mono text-[10px] flex items-center justify-center">
                              {step}
                            </span>
                            <span className="text-slate-700 dark:text-slate-300">{t(`land.how.${flow}${step}`)}</span>
                            {step < 6 && <ArrowRight size={12} className="ml-auto text-slate-300 dark:text-slate-600 hidden sm:block" />}
                          </li>
                        ))}
                      </ol>
                    </div>
                  ))}
                </div>

                {/* Mock playground */}
                <div className="lg:col-span-7 bg-white dark:bg-[#0b0f17] rounded-2xl border border-black/5 dark:border-white/10 p-5 flex flex-col min-h-[380px]">
                  <div className="flex items-center justify-between border-b border-black/5 dark:border-white/10 pb-3">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-slate-900 dark:bg-white animate-pulse" />
                      <span className="text-xs font-mono font-medium text-slate-800 dark:text-slate-200">{t("land.how.sessionLabel")}</span>
                    </div>
                    <span className="text-[11px] font-mono text-slate-600 dark:text-slate-300 bg-black/5 dark:bg-white/10 px-2 py-0.5 rounded">{t("land.how.demoTag")}</span>
                  </div>
                  <div className="space-y-4 py-5 flex-grow">
                    <div className="flex justify-end">
                      <p className="max-w-[80%] rounded-2xl rounded-br-md bg-slate-900 text-white dark:bg-white dark:text-black px-4 py-2.5 text-xs sm:text-sm">{t("land.how.q")}</p>
                    </div>
                    <div className="flex gap-3">
                      <span className="w-8 h-8 shrink-0 rounded-xl bg-black/5 dark:bg-white/10 flex items-center justify-center"><Bot size={15} /></span>
                      <div className="max-w-[85%] rounded-2xl rounded-bl-md border border-black/5 dark:border-white/10 bg-slate-50 dark:bg-[#111624] px-4 py-3 text-xs sm:text-sm text-slate-800 dark:text-slate-200">
                        {t("land.how.a")}
                      </div>
                    </div>
                  </div>
                  <div className="border-t border-black/5 dark:border-white/10 pt-4">
                    <p className={`${eyebrow} mb-2`}>{t("land.how.sourcesTitle")}</p>
                    <div className="grid sm:grid-cols-2 gap-2 font-mono text-[11px]">
                      <div className="rounded-lg border border-black/15 dark:border-white/20 bg-black/[0.03] dark:bg-white/[0.06] p-2.5">
                        <div className="flex items-center gap-1.5 font-semibold"><span className="opacity-60">[1]</span><FileText size={12} />{t("land.how.source1")}</div>
                        <div className="text-slate-500 dark:text-slate-400 mt-0.5">{t("land.how.source1Meta")}</div>
                      </div>
                      <div className="rounded-lg border border-black/5 dark:border-white/10 p-2.5 text-slate-500 dark:text-slate-400">
                        <div className="flex items-center gap-1.5 font-semibold text-slate-700 dark:text-slate-300"><span className="opacity-60">[2]</span><FileCode size={12} />{t("land.how.source2")}</div>
                        <div className="mt-0.5">{t("land.how.source2Meta")}</div>
                      </div>
                    </div>
                    <p className="text-[11px] text-slate-500 dark:text-slate-500 mt-3">{t("land.how.excerpt")}</p>
                  </div>
                </div>
              </div>
            </div>
          </section>

          {/* Channels */}
          <section id="channels" className="max-w-6xl mx-auto px-4 sm:px-6 py-16 relative z-10 scroll-mt-24">
            <div className="text-center max-w-2xl mx-auto mb-10">
              <span className={eyebrow}>{t("land.channels.eyebrow")}</span>
              <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight mt-2">{t("land.channels.title")}</h2>
              <p className="text-slate-600 dark:text-slate-400 text-xs sm:text-sm mt-2">{t("land.channels.sub")}</p>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {([
                { key: "c1", icon: Link2, code: "/share/{slug}/{token}" },
                { key: "c2", icon: Code2, code: "<script src=\"…/widget.js\">" },
                { key: "c3", icon: Send, code: "@your_bot" },
              ] as const).map(({ key, icon: Icon, code }) => (
                <div key={key} className={`${card} p-6`}>
                  <div className="w-10 h-10 rounded-xl bg-black/5 dark:bg-white/5 flex items-center justify-center text-slate-700 dark:text-slate-300 mb-4">
                    <Icon size={18} />
                  </div>
                  <h3 className="text-base font-semibold">{t(`land.channels.${key}.title`)}</h3>
                  <p className="text-xs sm:text-[13px] text-slate-500 dark:text-slate-400 mt-1.5 leading-relaxed">{t(`land.channels.${key}.text`)}</p>
                  <code className="mt-4 block rounded-lg bg-slate-100 dark:bg-[#07090e] border border-black/5 dark:border-white/5 px-3 py-2 font-mono text-[11px] text-slate-700 dark:text-slate-300 overflow-x-auto">{code}</code>
                </div>
              ))}
            </div>
          </section>

          {/* Stack */}
          <section id="stack" className="max-w-6xl mx-auto px-4 sm:px-6 py-16 relative z-10 scroll-mt-24">
            <div className="rounded-3xl p-8 bg-slate-900 text-white dark:bg-[#0b0f17] border border-black/10 dark:border-white/10 relative overflow-hidden">
              <div className="absolute -right-20 -top-20 w-72 h-72 rounded-full bg-white/[0.06] blur-3xl pointer-events-none" aria-hidden="true" />
              <div className="max-w-2xl relative">
                <span className="text-xs font-mono uppercase tracking-[0.2em] text-slate-400 block mb-2">{t("land.stack.eyebrow")}</span>
                <h3 className="text-2xl sm:text-3xl font-extrabold tracking-tight">{t("land.stack.title")}</h3>
                <p className="text-slate-400 text-xs sm:text-sm mt-2 mb-6">{t("land.stack.sub")}</p>
              </div>
              <div className="flex flex-wrap gap-2 mb-6 relative">
                {STACK.map((item) => (
                  <span key={item} className="px-3 py-1.5 rounded-full border border-white/10 bg-white/5 text-xs font-medium text-slate-200">{item}</span>
                ))}
              </div>
              <p className="text-[11px] font-mono uppercase tracking-widest text-slate-500 mb-2">{t("land.stack.snippet")}</p>
              <div className="bg-black/60 rounded-xl p-4 border border-white/10 font-mono text-xs text-slate-300 flex items-center justify-between gap-4 relative">
                <code className="overflow-x-auto select-all whitespace-nowrap">{EMBED}</code>
                <button type="button" onClick={() => void copyEmbed()} className="shrink-0 px-3 py-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white text-[11px] font-mono flex items-center gap-1.5 transition-colors">
                  {copied ? <Check size={13} /> : <Clipboard size={13} />}
                  <span>{copied ? t("land.stack.copied") : t("land.stack.copy")}</span>
                </button>
              </div>
            </div>
          </section>

          {/* CTA */}
          <section className="max-w-4xl mx-auto px-4 sm:px-6 py-24 text-center relative z-10">
            <h2 className="text-4xl sm:text-6xl font-extrabold tracking-tight mb-4">{t("land.cta.title")}</h2>
            <p className="text-slate-600 dark:text-slate-400 text-sm sm:text-base mb-8 max-w-md mx-auto">{t("land.cta.sub")}</p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Link href="/app" className={`${pill} px-9 py-3.5 text-xs sm:text-sm`}>
                {user ? t("land.nav.open") : t("land.cta.button")}
                <ArrowRight size={15} />
              </Link>
              <a href="#features" className={`${ghost} px-7 py-3.5 text-xs sm:text-sm font-medium`}>{t("land.nav.features")}</a>
            </div>
            <p className="mt-6 text-[11px] font-mono text-slate-500 dark:text-slate-500">{t("land.cta.accounts")}</p>
          </section>
        </main>

        <footer className="border-t border-black/5 dark:border-white/5 py-12 relative z-10 bg-white/50 dark:bg-[#050608]/80 text-xs text-slate-500">
          <div className="max-w-6xl mx-auto px-4 sm:px-6 flex flex-col sm:flex-row items-center justify-between gap-6">
            <div className="flex items-center gap-3">
              <span className="w-6 h-6 rounded-full border border-black/10 dark:border-white/15 flex items-center justify-center text-slate-800 dark:text-white text-[10px] font-bold">RC</span>
              <span className="font-semibold text-slate-900 dark:text-white">RAG Craft</span>
            </div>
            <div className="flex items-center gap-6">
              {nav.map((item) => (
                <a key={item.href} href={item.href} className="hover:text-black dark:hover:text-white transition-colors">{item.label}</a>
              ))}
            </div>
            <div className="text-center sm:text-right">
              <div>{t("land.footer.rights")}</div>
              <div className="font-mono text-[10px] uppercase tracking-widest mt-1">{t("land.footer.note")}</div>
            </div>
          </div>
        </footer>
      </div>
    </div>
  );
}
