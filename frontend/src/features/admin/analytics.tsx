"use client";
import { useEffect, useId, useMemo, useState, type ReactNode } from "react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  PolarAngleAxis,
  RadialBar,
  RadialBarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  Activity,
  ArrowUpRight,
  Bot,
  CircleHelp,
  Database,
  MessageSquare,
  Radio,
  Users,
  TrendingUp,
  TrendingDown,
  Minus,
} from "lucide-react";
import { analyticsService } from "@/services/analytics.service";
import { errorMessage } from "@/services/api";
import { chartPreview } from "@/mocks/chart-preview";
import type { AnalyticsOverview, ChartPoint } from "@/types/analytics";
import { useLabel, useLocale } from "@/i18n/context";

/* ---------- Theme ---------- */

interface ChartTheme {
  series: string[];
  rose: string;
  grid: string;
  axis: string;
  cursor: string;
  tipBg: string;
  tipBorder: string;
  tipText: string;
  track: string;
}

const THEMES: Record<"light" | "dark", ChartTheme> = {
  // Monochrome brand: shades of ink in light mode, shades of white in dark mode.
  light: {
    series: ["#0f172a", "#7b8798", "#b9c2cd", "#3f4b5c", "#d9dee5", "#59667a"],
    rose: "#c2410c",
    grid: "rgba(15, 23, 42, 0.07)",
    axis: "#8a95a3",
    cursor: "rgba(15, 23, 42, 0.04)",
    tipBg: "#ffffff",
    tipBorder: "rgba(15, 23, 42, 0.1)",
    tipText: "#0f172a",
    track: "rgba(15, 23, 42, 0.08)",
  },
  dark: {
    series: ["#ffffff", "#8c98a9", "#56637a", "#c9d1dc", "#3a4556", "#e7ebf0"],
    rose: "#f87171",
    grid: "rgba(255, 255, 255, 0.07)",
    axis: "#8c99a9",
    cursor: "rgba(255, 255, 255, 0.04)",
    tipBg: "#0f1420",
    tipBorder: "rgba(255, 255, 255, 0.12)",
    tipText: "#f1f5f9",
    track: "rgba(255, 255, 255, 0.1)",
  },
};

function statusColor(theme: ChartTheme, name: string, index: number) {
  const key = name.toUpperCase().replaceAll(" ", "_");
  const map: Record<string, string> = {
    READY: theme.series[0], ACTIVE: theme.series[0], LIVE: theme.series[0], CONNECTED: theme.series[0],
    FAILED: theme.rose, ERROR: theme.rose,
    DRAFT: theme.series[2], REVIEW: theme.series[2],
    PROCESSING: theme.series[1], QUEUED: theme.series[1], UPLOADING: theme.series[1], CHUNKING: theme.series[1], INDEXING: theme.series[1], CRAWLING: theme.series[1], EXTRACTING: theme.series[1],
    INACTIVE: theme.series[5], OFF: theme.series[5], DISCONNECTED: theme.series[5],
  };
  return map[key] || theme.series[index % theme.series.length];
}

/* ---------- Small building blocks ---------- */

interface TipRow { name?: string | number; value?: number | string; color?: string; payload?: Record<string, unknown> }
interface TipProps { active?: boolean; payload?: ReadonlyArray<TipRow>; label?: string | number }

function ChartTip({ active, payload, label, theme, labelFor }: TipProps & { theme: ChartTheme; labelFor: (name: string) => string }) {
  const { formatNumber } = useLocale();
  if (!active || !payload?.length) return null;
  const rows = payload.filter((row) => row.value !== undefined);
  const heading = label ?? (rows[0]?.payload?.name as string | undefined);
  return (
    <div className="rc-chart-tip" style={{ background: theme.tipBg, borderColor: theme.tipBorder, color: theme.tipText }}>
      {heading !== undefined && <strong>{labelFor(String(heading))}</strong>}
      {rows.map((row, index) => (
        <span key={index}>
          <i style={{ background: row.color || (row.payload?.fill as string) || theme.series[0] }} />
          {labelFor(String(row.name ?? ""))}
          <b>{formatNumber(Number(row.value))}</b>
        </span>
      ))}
    </div>
  );
}

function ChartCard({ title, subtitle, badge, children, wide = false }: { title: string; subtitle: string; badge?: string; children: ReactNode; wide?: boolean }) {
  return (
    <section className={`insight-card ${wide ? "insight-wide" : ""}`}>
      <header className="insight-heading">
        <div>
          <h3>{title}</h3>
          <p>{subtitle}</p>
        </div>
        {badge && <span className="insight-tag">{badge}</span>}
      </header>
      {children}
    </section>
  );
}

function EmptyChart() {
  const { t } = useLocale();
  return (
    <div className="insight-empty">
      <span className="insight-empty-icon"><Activity size={20} /></span>
      <strong>{t("an.empty.title")}</strong>
      <p>{t("an.empty.text")}</p>
    </div>
  );
}

function Distribution({ rows, theme, donut = false, unit }: { rows: ChartPoint[]; theme: ChartTheme; donut?: boolean; unit: string }) {
  const { t, formatNumber } = useLocale();
  const labelFor = useLabel();
  const total = rows.reduce((sum, row) => sum + row.value, 0);
  if (!total) return <EmptyChart />;

  if (donut)
    return (
      <div className="distribution-layout">
        <div className="ring-chart">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={rows}
                dataKey="value"
                nameKey="name"
                innerRadius="68%"
                outerRadius="92%"
                paddingAngle={3}
                cornerRadius={5}
                stroke="none"
                animationDuration={700}
                animationEasing="ease-out"
              >
                {rows.map((row, index) => (
                  <Cell key={row.name} fill={statusColor(theme, row.name, index)} />
                ))}
              </Pie>
              <Tooltip content={<ChartTip theme={theme} labelFor={labelFor} />} />
            </PieChart>
          </ResponsiveContainer>
          <div className="ring-center">
            <strong>{formatNumber(total)}</strong>
            <span>{t("common.total")}</span>
          </div>
        </div>
        <div className="chart-key">
          {rows.map((row, index) => {
            const color = statusColor(theme, row.name, index);
            const percent = Math.round((row.value / total) * 100);
            return (
              <div key={row.name}>
                <i style={{ background: color }} />
                <span>{labelFor(row.name)}</span>
                <em className="chart-key-track" style={{ background: theme.track }}>
                  <em style={{ width: `${percent}%`, background: color }} />
                </em>
                <strong>{formatNumber(row.value)}</strong>
                <small>{percent}%</small>
              </div>
            );
          })}
        </div>
      </div>
    );

  return (
    <div className="insight-canvas">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} margin={{ top: 8, right: 10, left: -25, bottom: 0 }} barSize={32}>
          <CartesianGrid stroke={theme.grid} vertical={false} />
          <XAxis dataKey="name" tick={{ fill: theme.axis, fontSize: 11 }} tickFormatter={labelFor} axisLine={false} tickLine={false} />
          <YAxis tick={{ fill: theme.axis, fontSize: 11 }} axisLine={false} tickLine={false} allowDecimals={false} />
          <Tooltip content={<ChartTip theme={theme} labelFor={labelFor} />} cursor={{ fill: theme.cursor }} />
          <Bar dataKey="value" name={unit} radius={[8, 8, 2, 2]} animationDuration={700}>
            {rows.map((row, index) => (
              <Cell key={row.name} fill={statusColor(theme, row.name, index)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function Readiness({ rows, theme }: { rows: ChartPoint[]; theme: ChartTheme }) {
  const { t, formatNumber } = useLocale();
  const labelFor = useLabel();
  const total = rows.reduce((sum, row) => sum + row.value, 0);
  if (!total) return <EmptyChart />;
  const live = rows.find((row) => row.name.toUpperCase() === "LIVE")?.value ?? 0;
  const percent = Math.round((live / total) * 100);
  return (
    <div className="rc-readiness">
      <div className="rc-gauge">
        <ResponsiveContainer width="100%" height="100%">
          <RadialBarChart
            data={[{ name: "live", value: percent, fill: theme.series[0] }]}
            innerRadius="72%"
            outerRadius="100%"
            startAngle={90}
            endAngle={-270}
            barSize={14}
          >
            <PolarAngleAxis type="number" domain={[0, 100]} tick={false} />
            <RadialBar dataKey="value" cornerRadius={12} background={{ fill: theme.track }} animationDuration={900} />
          </RadialBarChart>
        </ResponsiveContainer>
        <div className="ring-center">
          <strong>{percent}%</strong>
          <span>{t("an.livePercent")}</span>
        </div>
      </div>
      <div className="rc-readiness-side">
        <p className="rc-readiness-title">{t("an.liveOfTotal", { live, total })}</p>
        <div className="rc-stacked" style={{ background: theme.track }}>
          {rows.map((row, index) => (
            <span key={row.name} style={{ width: `${(row.value / total) * 100}%`, background: statusColor(theme, row.name, index) }} />
          ))}
        </div>
        <div className="chart-key">
          {rows.map((row, index) => (
            <div key={row.name}>
              <i style={{ background: statusColor(theme, row.name, index) }} />
              <span>{labelFor(row.name)}</span>
              <strong>{formatNumber(row.value)}</strong>
              <small>{Math.round((row.value / total) * 100)}%</small>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ---------- Main component ---------- */

export function Analytics({ admin = false, dark = false }: { admin?: boolean; dark?: boolean }) {
  const { t, locale, formatNumber } = useLocale();
  const labelFor = useLabel();
  const theme = THEMES[dark ? "dark" : "light"];
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
  const [error, setError] = useState("");
  const [days, setDays] = useState(14);
  const [sample, setSample] = useState(false);
  const chartId = useId().replaceAll(":", "");

  useEffect(() => {
    let active = true;
    const load = () =>
      analyticsService
        .overview(admin, days, locale)
        .then((data) => { if (active) { setOverview(data); setError(""); } })
        .catch((err) => { if (active) setError(errorMessage(err)); });
    void load();
    const update = () => { void load(); };
    window.addEventListener("ragcraft:data-changed", update);
    return () => { active = false; window.removeEventListener("ragcraft:data-changed", update); };
  }, [admin, days, locale]);

  const data = useMemo(() => (sample ? chartPreview(days, locale) : overview), [sample, days, locale, overview]);

  const trend = useMemo(() => {
    if (!data || days < 14) return null;
    const window = Math.floor(days / 2);
    const recent = data.daily.slice(-window).reduce((sum, day) => sum + day.messages, 0);
    const previous = data.daily.slice(-window * 2, -window).reduce((sum, day) => sum + day.messages, 0);
    if (!recent && !previous) return null;
    const percent = previous ? Math.round(((recent - previous) / previous) * 100) : 100;
    return { percent, window };
  }, [data, days]);

  if (error) return <p role="alert" className="error">{t(error)}</p>;
  if (!data) return <div className="insight-loading" role="status">{t("an.loading")}</div>;

  const periodTotal = data.daily.reduce((sum, day) => sum + day.messages, 0);
  const peak = data.daily.reduce((best, day) => (day.messages > best.messages ? day : best), data.daily[0]);
  const average = data.daily.length ? Math.round(periodTotal / data.daily.length) : 0;

  const metrics = [
    {
      label: admin ? t("an.platformUsers") : t("an.totalChatbots"),
      value: admin ? data.usage.total_users : data.usage.total_chatbots,
      description: admin
        ? t("an.enabledAccounts", { count: data.usage.active_users ?? 0 })
        : t("an.activeAssistants", { count: data.usage.active_chatbots ?? 0 }),
      icon: admin ? Users : Bot,
      color: theme.series[0],
    },
    {
      label: t("an.knowledgeSources"),
      value: data.usage.total_knowledge,
      description: t("an.uploadedDocuments", { count: data.usage.total_documents }),
      icon: Database,
      color: theme.series[1],
    },
    {
      label: t("an.totalMessages"),
      value: data.usage.total_messages,
      description: t("an.inLast7", { count: data.usage.messages_this_week ?? 0 }),
      icon: MessageSquare,
      color: theme.series[2],
      spark: true,
    },
    {
      label: t("an.liveChannels"),
      value: data.usage.active_channels,
      description: t("an.acrossChannels", { count: data.usage.total_channels ?? 0 }),
      icon: Radio,
      color: theme.series[3],
    },
  ];

  const TrendIcon = !trend ? Minus : trend.percent > 0 ? TrendingUp : trend.percent < 0 ? TrendingDown : Minus;
  const trendText = !trend
    ? null
    : trend.percent > 0
      ? t("an.trendUp", { percent: trend.percent, days: trend.window })
      : trend.percent < 0
        ? t("an.trendDown", { percent: Math.abs(trend.percent), days: trend.window })
        : t("an.trendFlat", { days: trend.window });

  return (
    <section className="insights">
      <div className="insights-toolbar">
        <div className="insights-caption">
          <span className={`data-mode ${sample ? "sample" : ""}`} />
          <span>{sample ? t("an.sampleCaption") : t("an.localCaption")}</span>
          <span className="insights-explainer">
            <CircleHelp size={13} />
            {sample ? t("an.sampleExplainer") : t("an.localExplainer")}
          </span>
        </div>
        <div className="insights-controls">
          <div className="segmented-control" aria-label={t("an.chartData")}>
            <button aria-pressed={!sample} className={!sample ? "chosen" : ""} onClick={() => setSample(false)}>{t("an.localData")}</button>
            <button aria-pressed={sample} className={sample ? "chosen" : ""} onClick={() => setSample(true)}>{t("an.samplePreview")}</button>
          </div>
          <select aria-label={t("an.range")} value={days} onChange={(event) => setDays(Number(event.target.value))}>
            <option value={7}>{t("an.last7")}</option>
            <option value={14}>{t("an.last14")}</option>
            <option value={30}>{t("an.last30")}</option>
          </select>
        </div>
      </div>

      <div className="insight-metrics">
        {metrics.map((metric) => (
          <article className="insight-metric" key={metric.label} style={{ "--metric-color": metric.color } as React.CSSProperties}>
            <div className="metric-top">
              <span>{metric.label}</span>
              <span className="metric-icon" style={{ color: metric.color }}>
                <metric.icon size={16} />
              </span>
            </div>
            <strong>{formatNumber(metric.value ?? 0)}</strong>
            {metric.spark && (
              <div className="metric-spark" aria-hidden="true">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={data.daily} margin={{ top: 2, right: 0, left: 0, bottom: 0 }}>
                    <defs>
                      <linearGradient id={`spark-${chartId}`} x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor={metric.color} stopOpacity={0.35} />
                        <stop offset="100%" stopColor={metric.color} stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <Area type="monotone" dataKey="messages" stroke={metric.color} strokeWidth={2} fill={`url(#spark-${chartId})`} isAnimationActive animationDuration={700} dot={false} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}
            <p>
              <span className="metric-dot" style={{ background: metric.color }} />
              {metric.description}
            </p>
          </article>
        ))}
      </div>

      <div className="insight-grid">
        <ChartCard title={t("an.activity.title")} subtitle={t("an.activity.subtitle")} badge={t("an.days", { count: days })} wide>
          <div className="activity-total">
            <strong>{formatNumber(periodTotal)}</strong>
            <span>{t("an.messagesInPeriod")}</span>
            {trendText && (
              <span className={`rc-trend ${trend && trend.percent > 0 ? "up" : trend && trend.percent < 0 ? "down" : ""}`}>
                <TrendIcon size={13} />
                {trendText}
              </span>
            )}
            <div className="inline-key">
              <span><i style={{ background: theme.series[0] }} />{t("an.published")}</span>
              <span><i style={{ background: theme.series[1] }} />{t("an.playground")}</span>
            </div>
          </div>
          <div className="insight-canvas activity-canvas">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={data.daily} margin={{ top: 15, right: 8, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id={`published-${chartId}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={theme.series[0]} stopOpacity={0.32} />
                    <stop offset="100%" stopColor={theme.series[0]} stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id={`playground-${chartId}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={theme.series[1]} stopOpacity={0.26} />
                    <stop offset="100%" stopColor={theme.series[1]} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={theme.grid} vertical={false} strokeDasharray="3 6" />
                <XAxis dataKey="label" tick={{ fill: theme.axis, fontSize: 11 }} axisLine={false} tickLine={false} minTickGap={32} />
                <YAxis tick={{ fill: theme.axis, fontSize: 11 }} axisLine={false} tickLine={false} allowDecimals={false} />
                <Tooltip content={<ChartTip theme={theme} labelFor={labelFor} />} cursor={{ stroke: theme.axis, strokeDasharray: "4 4" }} />
                <Area type="monotone" dataKey="published" name={t("an.published")} stroke={theme.series[0]} strokeWidth={2.5} fill={`url(#published-${chartId})`} activeDot={{ r: 5, strokeWidth: 2, stroke: theme.tipBg }} animationDuration={800} />
                <Area type="monotone" dataKey="playground" name={t("an.playground")} stroke={theme.series[1]} strokeWidth={2} fill={`url(#playground-${chartId})`} activeDot={{ r: 5, strokeWidth: 2, stroke: theme.tipBg }} animationDuration={800} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          {periodTotal > 0 && (
            <div className="rc-stat-strip">
              <span><small>{t("an.peakDay")}</small><strong>{peak.label}</strong><em>{formatNumber(peak.messages)}</em></span>
              <span><small>{t("an.dailyAverage")}</small><strong>{formatNumber(average)}</strong></span>
            </div>
          )}
          {!sample && !periodTotal && <p className="chart-footnote">{t("an.noMessages")}</p>}
        </ChartCard>

        <ChartCard title={t("an.channels.title")} subtitle={t("an.channels.subtitle")} badge={t("an.allTime")}>
          <Distribution rows={data.channels} theme={theme} donut unit={t("an.messages")} />
        </ChartCard>

        <ChartCard title={t("an.byAssistant.title")} subtitle={t("an.byAssistant.subtitle")} badge={t("an.top6")}>
          {data.chatbots.some((row) => row.value) ? (
            <div className="insight-canvas">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data.chatbots} layout="vertical" margin={{ top: 0, right: 20, left: 0, bottom: 0 }} barSize={14}>
                  <defs>
                    <linearGradient id={`bar-${chartId}`} x1="0" y1="0" x2="1" y2="0">
                      <stop offset="0%" stopColor={theme.series[0]} />
                      <stop offset="100%" stopColor={theme.series[3]} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke={theme.grid} horizontal={false} strokeDasharray="3 6" />
                  <XAxis type="number" tick={{ fill: theme.axis, fontSize: 11 }} axisLine={false} tickLine={false} allowDecimals={false} />
                  <YAxis type="category" dataKey="name" width={122} tick={{ fill: theme.axis, fontSize: 11 }} axisLine={false} tickLine={false} />
                  <Tooltip content={<ChartTip theme={theme} labelFor={labelFor} />} cursor={{ fill: theme.cursor }} />
                  <Bar dataKey="value" name={t("an.messages")} fill={`url(#bar-${chartId})`} radius={[0, 8, 8, 0]} animationDuration={700} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <EmptyChart />
          )}
        </ChartCard>

        <ChartCard title={t("an.sources.title")} subtitle={t("an.sources.subtitle")}>
          <Distribution rows={data.sourceTypes} theme={theme} unit={t("an.sourcesUnit")} />
        </ChartCard>

        <ChartCard title={t("an.processing.title")} subtitle={t("an.processing.subtitle")}>
          <Distribution rows={data.sourceStatus} theme={theme} donut unit={t("an.sourcesUnit")} />
        </ChartCard>

        <ChartCard title={t("an.readiness.title")} subtitle={t("an.readiness.subtitle")} badge={t("an.currentStatus")} wide>
          <Readiness rows={data.channelStatus} theme={theme} />
        </ChartCard>

        <ChartCard title={t("an.health.title")} subtitle={t("an.health.subtitle")}>
          <Distribution rows={data.botStatus} theme={theme} donut unit={t("an.totalChatbots")} />
          <div className="chart-footnote">
            <ArrowUpRight size={13} />
            {t("an.health.footnote")}
          </div>
        </ChartCard>
      </div>
    </section>
  );
}
