"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import type { CurrentUser } from "@/types/auth";
import type { Chatbot, Dashboard } from "@/types/chatbot";
import { chatbotService } from "@/services/chatbot.service";
import { userService } from "@/services/user.service";
import { errorMessage } from "@/services/api";
import { ChatbotList } from "@/features/chatbots/chatbot-list";
import { ChatbotForm } from "@/features/chatbots/chatbot-form";
import { UserManagement, Monitoring } from "@/features/admin/users";
import { Analytics } from "@/features/admin/analytics";
import { ContextSection } from "./context-section";
import { Operations } from "@/features/admin/operations";
import { Catalog } from "@/features/admin/catalog";
import { Profile } from "@/features/profile/profile";
import { RecentActivity } from "@/features/knowledge/recent-activity";
import { ChatbotDetail } from "@/features/chatbots/chatbot-detail";
import { Avatar } from "@/components/avatar";
import { UserMenu } from "@/components/user-menu";
import { LanguageSwitcher } from "@/components/language-switcher";
import { useLocale } from "@/i18n/context";
import { applyHtmlTheme } from "@/features/landing/use-site-theme";
import {
  LayoutDashboard,
  Bot,
  Settings,
  Plus,
  Building2,
  LogOut,
  ChevronRight,
  Shield,
  FileText,
  AlertCircle,
  Sparkles,
  RefreshCw,
  Cpu,
  User as UserIcon,
  Layers,
  ArrowRight,
  Database,
  BarChart3,
  Server,
  Moon,
  Sun,
} from "lucide-react";

type Page =
  | "Dashboard" | "Chatbots" | "Knowledge" | "Playground" | "Channels" | "Analytics" | "Settings"
  | "Users" | "Models" | "System Prompts" | "Monitoring" | "System";

const PAGE_KEYS: Record<Page, string> = {
  Dashboard: "nav.dashboard", Chatbots: "nav.chatbots", Knowledge: "nav.knowledge", Playground: "nav.playground",
  Channels: "nav.channels", Analytics: "nav.analytics", Settings: "nav.settings", Users: "nav.users",
  Models: "nav.models", "System Prompts": "nav.systemPrompts", Monitoring: "nav.monitoring", System: "nav.system",
};

const PAGE_SUBTITLES: Record<Page, string> = {
  Dashboard: "page.dashboard.subtitle", Chatbots: "page.chatbots.subtitle", Knowledge: "page.knowledge.subtitle",
  Playground: "page.playground.subtitle", Channels: "page.channels.subtitle", Analytics: "page.analytics.subtitle",
  Settings: "page.settings.subtitle", Users: "page.users.subtitle", Models: "page.models.subtitle",
  "System Prompts": "page.systemPrompts.subtitle", Monitoring: "page.monitoring.subtitle", System: "page.system.subtitle",
};

export function Workspace({
  user,
  onLogout,
  onProfileChanged,
}: {
  user: CurrentUser;
  onLogout: () => Promise<void>;
  onProfileChanged: (user: CurrentUser) => void;
}) {
  const { t } = useLocale();
  const [contextId, setContextId] = useState("");
  const [page, setPage] = useState<Page>("Dashboard");
  const [bots, setBots] = useState<Chatbot[]>([]);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [selected, setSelected] = useState<Chatbot | null>(null);
  const [creating, setCreating] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loggingOut, setLoggingOut] = useState(false);
  const admin = user.role === "ADMIN";
  const dark = user.theme === "dark";

  useEffect(() => {
    applyHtmlTheme(dark);
  }, [dark]);

  async function refresh() {
    try {
      const [list, summary] = await Promise.all([chatbotService.list(), chatbotService.dashboard()]);
      setBots(list);
      setDashboard(summary);
      setError("");
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let active = true;
    Promise.all([chatbotService.list(), chatbotService.dashboard()])
      .then(([list, summary]) => {
        if (active) {
          setBots(list);
          setDashboard(summary);
        }
      })
      .catch((error) => {
        if (active) setError(errorMessage(error));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  function navigate(name: Page) {
    setPage(name);
    setSelected(null);
    setCreating(false);
    void refresh();
  }

  function select(bot: Chatbot) {
    setSelected(bot);
    setContextId(bot.id);
    setPage("Chatbots");
    setCreating(false);
  }

  function saved(bot: Chatbot) {
    setNotice(t("dash.saved"));
    select(bot);
    void refresh();
  }

  async function logout() {
    setLoggingOut(true);
    await onLogout();
    setLoggingOut(false);
  }

  async function toggleTheme() {
    try {
      onProfileChanged(await userService.setTheme(dark ? "light" : "dark"));
    } catch (error) {
      setError(errorMessage(error));
    }
  }

  const userNav: { name: Page; icon: typeof Bot }[] = [
    { name: "Dashboard", icon: LayoutDashboard },
    { name: "Chatbots", icon: Bot },
    { name: "Knowledge", icon: Database },
    { name: "Playground", icon: Sparkles },
    { name: "Channels", icon: Layers },
    { name: "Analytics", icon: BarChart3 },
    { name: "Settings", icon: Settings },
  ];
  const adminNav: { name: Page; icon: typeof Bot }[] = [
    { name: "Users", icon: UserIcon },
    { name: "Models", icon: Cpu },
    { name: "System Prompts", icon: FileText },
    { name: "Monitoring", icon: Shield },
    { name: "System", icon: Server },
  ];

  const displayName = user.display_name || user.full_name || (admin ? t("nav.administrator") : t("nav.member"));
  const greetingName = user.display_name || user.full_name.split(" ")[0] || t("dash.greetingFallback");

  const heading =
    page === "Dashboard" ? (admin ? t("page.dashboard.adminTitle") : t("page.dashboard.title"))
      : page === "Chatbots" ? t("page.chatbots.title")
        : page === "Settings" ? t("page.settings.title")
          : t(PAGE_KEYS[page]);

  const renderNav = (items: { name: Page; icon: typeof Bot }[]) =>
    items.map(({ name, icon: Icon }) => (
      <button
        key={name}
        className={page === name && !selected && !creating ? "active" : ""}
        onClick={() => navigate(name)}
      >
        <Icon style={{ width: "17px", height: "17px" }} aria-hidden="true" />
        {t(PAGE_KEYS[name])}
      </button>
    ));

  return (
    <div className={`app-layout ${dark ? "demo-dark" : ""}`}>
      <aside className="sidebar">
        <Link className="brand" href="/" title={t("auth.backHome")}>
          <span className="brand-mark">RC</span>
          <span>
            {t("app.name")}
            <small>{t("app.tagline")}</small>
          </span>
        </Link>

        <div className="workspace-label">
          <div className="organization-icon">
            <Building2 style={{ width: "16px", height: "16px" }} />
          </div>
          <div>
            {user.organization_name}
            <small>{admin ? t("nav.adminWorkspace") : t("nav.personalWorkspace")}</small>
          </div>
        </div>

        <p className="sidebar-label">{t("nav.workspace")}</p>
        <nav aria-label="Main navigation">{renderNav(userNav)}</nav>
        {admin && (
          <>
            <p className="sidebar-label sidebar-label-admin">{t("nav.administration")}</p>
            <nav aria-label="Administration navigation">{renderNav(adminNav)}</nav>
          </>
        )}

        <div className="sidebar-bottom">
          <div className="version-note">
            <span className="live-dot" />
            {t("app.demoEnvironment")} · {t("app.version")}
          </div>
          <button type="button" className="account account-button" onClick={() => navigate("Settings")} title={t("menu.profile")}>
            <Avatar name={displayName} email={user.email} image={user.avatar} size={34} />
            <div>
              <strong>{displayName}</strong>
              <small>{user.email}</small>
            </div>
          </button>
          <button className="text-button sidebar-signout" disabled={loggingOut} onClick={() => void logout()}>
            <LogOut style={{ width: "14px", height: "14px" }} />
            {loggingOut ? t("nav.signingOut") : t("nav.signOut")}
          </button>
        </div>
      </aside>

      <div className="main-column">
        <div className="topbar">
          <div className="breadcrumb">
            <span>{t("topbar.workspace")}</span>
            <ChevronRight className="breadcrumb-sep" style={{ width: "14px", height: "14px" }} />
            <span className="breadcrumb-current">
              {selected?.name || (creating ? t("topbar.newAssistant") : heading)}
            </span>
          </div>
          <div className="topbar-meta">
            <span className="environment-pill"><span />{t("app.demoEnvironmentLower")}</span>
            <LanguageSwitcher compact />
            <button
              type="button"
              className="rc-icon-button"
              onClick={() => void toggleTheme()}
              aria-label={dark ? t("theme.switchToLight") : t("theme.switchToDark")}
              title={dark ? t("theme.switchToLight") : t("theme.switchToDark")}
            >
              {dark ? <Sun size={16} /> : <Moon size={16} />}
            </button>
            <UserMenu
              user={user}
              loggingOut={loggingOut}
              onOpenProfile={() => navigate("Settings")}
              onToggleTheme={() => void toggleTheme()}
              onLogout={() => void logout()}
            />
          </div>
        </div>

        <main className="page-content">
          {notice && (
            <p className="success-note" role="status">
              {notice}
              <button className="text-button" onClick={() => setNotice("")}>
                {t("common.dismiss")}
              </button>
            </p>
          )}
          {error && (
            <div className="error" role="alert" style={{ marginBottom: "24px" }}>
              <AlertCircle style={{ width: "16px", height: "16px", flexShrink: 0 }} />
              <span style={{ flex: 1 }}>{t(error)}</span>
              <button className="rc-retry" onClick={() => void refresh()}>
                <RefreshCw style={{ width: "12px", height: "12px" }} />
                {t("common.retry")}
              </button>
            </div>
          )}

          {selected ? (
            <ChatbotDetail
              key={selected.id}
              bot={selected}
              onChanged={(updated) => { setSelected(updated); setContextId(updated.id); void refresh(); }}
              onBack={() => navigate("Chatbots")}
              onDeleted={() => {
                setNotice(t("dash.deleted"));
                navigate("Chatbots");
              }}
            />
          ) : creating ? (
            <>
              <header className="page-heading">
                <div>
                  <p className="eyebrow">
                    <Sparkles style={{ width: "13px", height: "13px" }} />
                    {t("page.create.eyebrow")}
                  </p>
                  <h1>{t("page.create.title")}</h1>
                  <p className="muted">{t("page.create.subtitle")}</p>
                </div>
              </header>
              <ChatbotForm onSaved={saved} onCancel={() => setCreating(false)} />
            </>
          ) : (
            <>
              <header className="page-heading">
                <div>
                  <p className="eyebrow">
                    <Layers style={{ width: "13px", height: "13px" }} />
                    {t("page.yourWorkspace")}
                  </p>
                  <h1>{heading}</h1>
                  <p className="muted">{t(PAGE_SUBTITLES[page])}</p>
                </div>
                {page === "Chatbots" && (
                  <button className="primary" onClick={() => setCreating(true)}>
                    <Plus style={{ width: "16px", height: "16px" }} />
                    {t("page.createChatbot")}
                  </button>
                )}
              </header>

              {loading && (
                <p role="status" className="muted rc-loading-row">
                  <RefreshCw style={{ width: "16px", height: "16px", animation: "spin 1s linear infinite" }} />
                  {t("dash.loading")}
                </p>
              )}

              {!loading && page === "Dashboard" && (
                <>
                  <section className="dashboard-welcome">
                    <div>
                      <span className="welcome-kicker">{t("dash.kicker")}</span>
                      <h2>{t("dash.greeting", { name: greetingName })}</h2>
                      <p>{t("dash.text")}</p>
                      <div className="welcome-actions">
                        <button className="primary" onClick={() => setCreating(true)}>
                          <Plus size={15} />
                          {t("dash.createAssistant")}
                        </button>
                        <button onClick={() => navigate("Playground")}>
                          <Sparkles size={15} />
                          {t("dash.openPlayground")}
                          <ArrowRight size={14} />
                        </button>
                      </div>
                    </div>
                    <div className="welcome-art" aria-hidden="true">
                      <div className="art-orbit orbit-one" />
                      <div className="art-orbit orbit-two" />
                      <div className="art-center"><Bot size={34} /></div>
                      <span className="art-node node-document"><FileText size={20} /></span>
                      <span className="art-node node-knowledge"><Database size={20} /></span>
                      <span className="art-node node-spark"><Sparkles size={20} /></span>
                    </div>
                  </section>
                  <div className="overview-heading">
                    <h2>{t("dash.insights")}</h2>
                    <span>{t("dash.insightsCaption")}</span>
                  </div>
                  <Analytics admin={admin} dark={dark} />

                  <div className="section-heading" style={{ marginTop: "32px" }}>
                    <div>
                      <div className="eyebrow">
                        <Bot style={{ width: "13px", height: "13px" }} />
                        {t("dash.recentEyebrow")}
                      </div>
                      <h2>{t("dash.recentTitle")}</h2>
                    </div>
                    <button className="text-button rc-inline" onClick={() => navigate("Chatbots")}>
                      {t("dash.viewAll", { count: bots.length })}
                      <ArrowRight style={{ width: "14px", height: "14px" }} />
                    </button>
                  </div>

                  <ChatbotList bots={dashboard?.recent_chatbots || []} onSelect={select} onCreate={() => setCreating(true)} />

                  <div style={{ marginTop: "32px" }}>
                    <RecentActivity
                      onSelect={(id) => {
                        const bot = bots.find((b) => b.id === id);
                        if (bot) select(bot);
                      }}
                    />
                  </div>
                </>
              )}

              {!loading && page === "Chatbots" && (
                <ChatbotList bots={bots} onSelect={select} onCreate={() => setCreating(true)} />
              )}

              {["Knowledge", "Playground", "Channels"].includes(page) && (
                <ContextSection
                  selected={contextId}
                  onSelect={setContextId}
                  page={page as "Knowledge" | "Playground" | "Channels"}
                  bots={bots}
                  onCreate={() => setCreating(true)}
                  onChanged={() => void refresh()}
                />
              )}
              {page === "Analytics" && <Analytics admin={admin} dark={dark} />}
              {admin && page === "System" && <Operations />}
              {admin && page === "Users" && <UserManagement />}
              {admin && page === "Monitoring" && (
                <Monitoring onOpen={(id) => { const bot = bots.find((b) => b.id === id); if (bot) select(bot); }} />
              )}
              {admin && (page === "Models" || page === "System Prompts") && <Catalog key={page} kind={page} />}
              {page === "Settings" && <Profile user={user} onChanged={onProfileChanged} />}
              {page === "Settings" && (
                <section className="panel" style={{ maxWidth: "800px" }}>
                  <div className="section-heading" style={{ marginBottom: "16px" }}>
                    <h2>{t("settings.workspaceInfo")}</h2>
                  </div>
                  <dl className="detail-grid">
                    <div>
                      <dt className="rc-inline"><Building2 style={{ width: "13px", height: "13px" }} />{t("settings.organization")}</dt>
                      <dd>{user.organization_name}</dd>
                    </div>
                    <div>
                      <dt className="rc-inline"><UserIcon style={{ width: "13px", height: "13px" }} />{t("settings.accountEmail")}</dt>
                      <dd>{user.email}</dd>
                    </div>
                    <div>
                      <dt className="rc-inline"><Shield style={{ width: "13px", height: "13px" }} />{t("settings.role")}</dt>
                      <dd>{t(`role.${user.role}`)}</dd>
                    </div>
                    <div>
                      <dt className="rc-inline"><Cpu style={{ width: "13px", height: "13px" }} />{t("settings.platformVersion")}</dt>
                      <dd>RAG Craft {t("app.version")}</dd>
                    </div>
                  </dl>
                  <p className="muted" style={{ fontSize: "12px", marginTop: "16px" }}>{t("settings.demoNote")}</p>
                </section>
              )}
            </>
          )}
        </main>

        <footer className="app-footer">
          <span>{t("app.footerLeft")}</span>
          <span>{t("app.footerRight")}</span>
        </footer>
      </div>
    </div>
  );
}
