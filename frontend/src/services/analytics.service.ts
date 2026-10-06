import { accessibleBots, currentAccount, requireAdmin } from './access';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { conversationRepository, analyticsRepository } from '@/repositories/conversation.repository';
import { userRepository } from '@/repositories/auth.repository';
export const analyticsService = {
    async overview(admin = false, days = 14, locale?: string): Promise<import('@/types/analytics').AnalyticsOverview> {
        const usage = await this.usage(admin);
        const bots = accessibleBots();
        const botIds = new Set(bots.map(bot => bot.id));
        const events = analyticsRepository.all().filter(event => botIds.has(event.chatbot_id));
        const sources = knowledgeRepository.all().filter(source => botIds.has(source.chatbot_id));
        const daily = Array.from({ length: days }, (_, index) => {
            const date = new Date(); date.setHours(0, 0, 0, 0); date.setDate(date.getDate() - days + index + 1);
            const next = new Date(date); next.setDate(next.getDate() + 1);
            const rows = events.filter(event => Date.parse(event.created_at) >= date.getTime() && Date.parse(event.created_at) < next.getTime());
            return { date: date.toISOString(), label: date.toLocaleDateString(locale, { month: 'short', day: 'numeric' }), messages: rows.length * 2,
                playground: rows.filter(event => event.channel === 'PLAYGROUND').length * 2, published: rows.filter(event => event.channel !== 'PLAYGROUND').length * 2 };
        });
        const group = (values: string[]) => Array.from(new Set(values)).map(name => ({ name, value: values.filter(value => value === name).length }));
        return { usage, daily, channelStatus: [{ name: 'Live', value: usage.active_channels }, { name: 'Off', value: usage.total_channels - usage.active_channels }], channels: Object.entries(usage.messages_by_channel).map(([name, value]) => ({ name: name.replaceAll('_', ' '), value })),
            chatbots: bots.map(bot => ({ name: bot.name, value: events.filter(event => event.chatbot_id === bot.id).length * 2 })).sort((a,b) => b.value - a.value).slice(0, 6),
            botStatus: group(bots.map(bot => bot.status)), sourceTypes: group(sources.map(source => source.file_type)), sourceStatus: group(sources.map(source => source.status)) };
    },
    async usage(admin = false) {
        currentAccount();
        if (admin)
            requireAdmin();
        const bots = accessibleBots();
        const ids = new Set(bots.map(b => b.id));
        const events = analyticsRepository.all().filter(e => ids.has(e.chatbot_id));
        const channels = channelRepository.all().filter(c => ids.has(c.chatbot_id));
        const knowledge = knowledgeRepository.all().filter(d => ids.has(d.chatbot_id));
        return { total_chatbots: bots.length, active_chatbots: bots.filter(b => b.status === 'ACTIVE').length, total_documents: knowledge.filter(d => d.file_type !== 'WEBSITE').length, total_knowledge: knowledge.length, total_conversations: conversationRepository.all().filter(c => ids.has(c.chatbot_id)).length, total_messages: events.length * 2, messages_this_week: events.filter(e => Date.parse(e.created_at) > Date.now() - 7 * 86400000).length * 2, total_channels: channels.length, active_channels: channels.filter(c => c.enabled).length,
            ...(admin ? { total_users: userRepository.all().length, active_users: userRepository.all().filter(u => u.is_active).length } : {}),
            messages_by_channel: Object.fromEntries(['PLAYGROUND', 'PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM'].map(channel => [channel, events.filter(e => e.channel === channel).length * 2])),
            messages_by_chatbot: Object.fromEntries(bots.map(b => [b.name, events.filter(e => e.chatbot_id === b.id).length * 2])) };
    },
};
