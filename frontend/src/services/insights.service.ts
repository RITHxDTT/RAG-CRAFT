import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { analyticsRepository } from '@/repositories/conversation.repository';
import { userRepository } from '@/repositories/auth.repository';
import { appealRepository, feedbackRepository } from '@/repositories/v5.repository';
import { requireAdmin, requireBot } from './access';
import { platformSettings } from './platform-settings';
import { storageUsed } from './quota.service';
import type { AnalyticsEvent } from '@/types/demo';
const DAY = 86400000, ACTIVE_WINDOW = 30 * 60000;
const CHANNELS = ['PLAYGROUND', 'PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM'] as const;
// Each event is one question and one answer, matching the message counts used elsewhere in the demo.
const messages = (events: AnalyticsEvent[]) => events.length * 2;
const startOfDay = (time: number) => { const date = new Date(time); date.setHours(0, 0, 0, 0); return date.getTime(); };
const distinct = (values: (string | undefined)[]) => new Set(values.filter(Boolean)).size;
const activeSessions = (events: AnalyticsEvent[]) => distinct(events.filter(event => Date.parse(event.created_at) > Date.now() - ACTIVE_WINDOW).map(event => event.session_id));
export type Granularity = 'DAILY' | 'WEEKLY' | 'MONTHLY';
export interface TrendPoint { date: string; messages: number; sessions: number }
/** Buckets events into the last `count` days, weeks (rolling 7 days) or calendar months. */
export function trend(events: AnalyticsEvent[], granularity: Granularity, count?: number): TrendPoint[] {
    const buckets = count ?? { DAILY: 14, WEEKLY: 8, MONTHLY: 6 }[granularity];
    return Array.from({ length: buckets }, (_, index) => {
        const back = buckets - 1 - index;
        let from: number, to: number;
        if (granularity === 'MONTHLY') {
            const first = new Date(); first.setDate(1); first.setHours(0, 0, 0, 0);
            from = new Date(first.getFullYear(), first.getMonth() - back, 1).getTime();
            to = new Date(first.getFullYear(), first.getMonth() - back + 1, 1).getTime();
        }
        else {
            const span = granularity === 'WEEKLY' ? 7 : 1;
            to = startOfDay(Date.now()) + DAY - back * span * DAY;
            from = to - span * DAY;
        }
        const rows = events.filter(event => Date.parse(event.created_at) >= from && Date.parse(event.created_at) < to);
        return { date: new Date(from).toISOString(), messages: messages(rows), sessions: distinct(rows.map(event => event.session_id)) };
    });
}
function ranked(values: string[], limit = 10) {
    const counts = new Map<string, number>();
    values.forEach(value => counts.set(value, (counts.get(value) ?? 0) + 1));
    return [...counts].map(([question, count]) => ({ question, count })).sort((a, b) => b.count - a.count || a.question.localeCompare(b.question)).slice(0, limit);
}
const botEvents = (botId: string) => analyticsRepository.all().filter(event => event.chatbot_id === botId);
export const insightsService = {
    /** Per-chatbot overview: knowledge size, traffic, guests and sessions, with daily/weekly/monthly trends. */
    async botOverview(botId: string) {
        requireBot(botId);
        const docs = knowledgeRepository.all().filter(doc => doc.chatbot_id === botId);
        const events = botEvents(botId);
        return { total_documents: docs.length, storage_used: docs.reduce((sum, doc) => sum + doc.size_bytes, 0), index_size_chunks: docs.reduce((sum, doc) => sum + (doc.chunkCount || 0), 0),
            total_messages: messages(events), sessions: distinct(events.map(event => event.session_id)), guests: distinct(events.map(event => event.guest_id)), active_sessions: activeSessions(events),
            trends: { DAILY: trend(events, 'DAILY'), WEEKLY: trend(events, 'WEEKLY'), MONTHLY: trend(events, 'MONTHLY') } };
    },
    async channelBreakdown(botId: string) {
        requireBot(botId);
        const events = botEvents(botId);
        return CHANNELS.map(channel => { const rows = events.filter(event => event.channel === channel); return { channel, messages: messages(rows), sessions: distinct(rows.map(event => event.session_id)) }; });
    },
    /** Most-asked and unanswered questions, helpful/not-helpful counts and report totals. */
    async answerQuality(botId: string) {
        requireBot(botId);
        const events = botEvents(botId).filter(event => event.question);
        const feedback = feedbackRepository.all().filter(row => row.chatbot_id === botId);
        const reports = feedback.filter(row => row.kind === 'REPORT');
        return { most_asked: ranked(events.map(event => event.question!.toLowerCase())), unanswered: ranked(events.filter(event => event.unanswered).map(event => event.question!.toLowerCase())),
            helpful: feedback.filter(row => row.rating === 'HELPFUL').length, not_helpful: feedback.filter(row => row.rating === 'NOT_HELPFUL').length,
            reports_total: reports.length, reports_open: reports.filter(row => row.status === 'OPEN').length };
    },
    /** Admin overview: users, chatbots, today vs yesterday and month vs last month, open reports, pending appeals. */
    async platform() {
        requireAdmin();
        const users = userRepository.all(), bots = chatbotRepository.all(), events = analyticsRepository.all();
        const today = startOfDay(Date.now()), month = new Date(today); month.setDate(1);
        const lastMonth = new Date(month); lastMonth.setMonth(month.getMonth() - 1);
        const between = (from: number, to: number) => events.filter(event => Date.parse(event.created_at) >= from && Date.parse(event.created_at) < to);
        const byUser = users.map(user => ({ user_id: user.id, email: user.email, tokens: events.filter(event => event.ownerId === user.id).reduce((sum, event) => sum + (event.tokens ?? 0), 0) }))
            .filter(row => row.tokens > 0).sort((a, b) => b.tokens - a.tokens);
        return { total_users: users.length, active_users: users.filter(user => user.status === 'ACTIVE').length, unverified_users: users.filter(user => user.status === 'UNVERIFIED').length,
            total_chatbots: bots.length, published_chatbots: bots.filter(bot => bot.status === 'ACTIVE').length,
            messages_today: messages(between(today, Infinity)), messages_yesterday: messages(between(today - DAY, today)),
            messages_this_month: messages(between(month.getTime(), Infinity)), messages_last_month: messages(between(lastMonth.getTime(), month.getTime())),
            open_reports: feedbackRepository.all().filter(row => row.kind === 'REPORT' && row.status === 'OPEN').length, pending_appeals: appealRepository.all().filter(row => row.status === 'PENDING').length,
            storage_used: users.reduce((sum, user) => sum + storageUsed(user.id), 0), token_usage: events.reduce((sum, event) => sum + (event.tokens ?? 0), 0), top_users_by_tokens: byUser.slice(0, 5) };
    },
    /** Live platform monitoring: active sessions, daily volume, channel activity and LLM provider status. */
    async monitoring() {
        requireAdmin();
        const events = analyticsRepository.all(), settings = platformSettings();
        return { active_sessions: activeSessions(events), daily_messages: trend(events, 'DAILY'),
            channel_activity: (['PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM'] as const).map(channel => ({ channel, messages: messages(events.filter(event => event.channel === channel)), live_channels: channelRepository.all().filter(row => row.channel === channel && row.enabled).length })),
            llm: Object.entries(settings.llm_status).map(([provider, status]) => ({ provider, status })), system_fallback_model: settings.system_fallback_model,
            alerts: Object.entries(settings.llm_status).filter(([, status]) => status !== 'OPERATIONAL').map(([provider, status]) => `${provider} is ${status.toLowerCase()}.`) };
    },
};
