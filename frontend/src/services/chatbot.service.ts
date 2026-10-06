import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { conversationRepository } from '@/repositories/conversation.repository';
import { fileStorage } from '@/storage/indexed-db';
import { currentAccount, accessibleBots, requireBot } from './access';
import { defaultSettings } from '@/mocks/seed';
import { delay, id, now, required, toast } from '@/utils/demo';
import type { Chatbot, ChatbotInput } from '@/types/chatbot';
function counts(bot: Chatbot) {
    const docs = knowledgeRepository.all().filter(doc => doc.chatbot_id === bot.id);
    return { ...bot, document_count: docs.length, ready_count: docs.filter(doc => doc.status === 'READY').length, failed_count: docs.filter(doc => doc.status === 'FAILED').length };
}
export const chatbotService = {
    async list() { return accessibleBots().map(counts).sort((a, b) => b.updated_at.localeCompare(a.updated_at)); },
    async dashboard() { const bots = await this.list(); return { total_chatbots: bots.length, total_documents: bots.reduce((sum, b) => sum + b.document_count, 0), ready_documents: bots.reduce((sum, b) => sum + b.ready_count, 0), failed_documents: bots.reduce((sum, b) => sum + b.failed_count, 0), recent_chatbots: bots.slice(0, 6) }; },
    async get(botId: string) { return counts(requireBot(botId)); },
    async create(data: ChatbotInput) {
        await delay();
        const user = currentAccount();
        const date = now();
        const bot: Chatbot = { ...data, id: id('bot'), ownerId: user.id, organization_id: user.organization_id,
            name: required(data.name, 'Chatbot name'), status: data.status ?? 'DRAFT', settings: { ...defaultSettings, ...data.settings },
            created_at: date, updated_at: date, document_count: 0, ready_count: 0, failed_count: 0 };
        chatbotRepository.put(bot);
        toast('Chatbot created.');
        return bot;
    },
    async update(botId: string, data: ChatbotInput) {
        await delay();
        const bot = requireBot(botId);
        const updated = { ...bot, ...data, name: required(data.name, 'Chatbot name'), settings: { ...bot.settings, ...data.settings }, updated_at: now() };
        chatbotRepository.put(updated);
        toast('Settings saved.');
        return counts(updated);
    },
    async duplicate(botId: string) { const bot = requireBot(botId); return this.create({ name: `${bot.name} (copy)`, description: bot.description, avatar: bot.avatar, settings: bot.settings, starter_questions: bot.starter_questions }); },
    async delete(botId: string) {
        await delay();
        requireBot(botId);
        const docs = knowledgeRepository.all().filter(doc => doc.chatbot_id === botId);
        await Promise.all(docs.map(doc => fileStorage.remove(doc.id)));
        knowledgeRepository.save(knowledgeRepository.all().filter(doc => doc.chatbot_id !== botId));
        channelRepository.save(channelRepository.all().filter(row => row.chatbot_id !== botId));
        conversationRepository.save(conversationRepository.all().filter(row => row.chatbot_id !== botId));
        chatbotRepository.remove(botId);
        toast('Chatbot deleted.');
    },
};
