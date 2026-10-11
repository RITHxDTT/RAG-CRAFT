import { apiMode } from '@/config/data-source';
import { apiChatbotService } from './api/chatbot.api';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { analyticsRepository } from '@/repositories/conversation.repository';
import { currentAccount, accessibleBots, requireBot } from './access';
import { catalogService } from './catalog.service';
import { applyAdvancedSettings } from './platform-settings';
import { assertCanCreateBot, warnIfNearLimit } from './quota.service';
import { assertTransition, publishChecklist } from './bot-lifecycle';
import { deleteBotData } from './account-data';
import { ApiError } from './api';
import { defaultSettings } from '@/mocks/defaults';
import { delay, id, now, required, toast } from '@/utils/demo';
import type { BotSettings, Chatbot, ChatbotInput, ChatbotStatus } from '@/types/chatbot';
const TONES = ['PROFESSIONAL', 'FRIENDLY', 'CASUAL', 'FORMAL', 'CUSTOM'], LENGTHS = ['CONCISE', 'DETAILED'], LANGUAGES = ['AUTO', 'EN', 'KM', 'KO'], MODES = ['SEMANTIC', 'KEYWORD', 'HYBRID'];
const oneOf = (value: string | undefined, allowed: string[], label: string) => { if (value !== undefined && !allowed.includes(value)) throw new Error(`${label} must be one of ${allowed.join(', ')}.`); };
function counts(bot: Chatbot): Chatbot {
    const docs = knowledgeRepository.all().filter(doc => doc.chatbot_id === bot.id);
    const channels = channelRepository.all().filter(channel => channel.chatbot_id === bot.id);
    const failed = docs.filter(doc => doc.status === 'FAILED').length, ready = docs.filter(doc => doc.status === 'READY').length;
    return { ...bot, document_count: docs.length, ready_count: ready, failed_count: failed, channel_count: channels.length, channels: channels.map(channel => channel.channel),
        message_count: analyticsRepository.all().filter(event => event.chatbot_id === bot.id).length * 2,
        kb_status: !docs.length ? 'EMPTY' : failed ? 'FAILED' : ready === docs.length ? 'READY' : 'PROCESSING' };
}
/** Validates and merges settings; the embedding model is fixed once the chatbot exists. */
async function resolveSettings(input: Partial<BotSettings> = {}, existing?: BotSettings): Promise<BotSettings> {
    const merged = { ...(existing ?? defaultSettings), ...input } as BotSettings;
    oneOf(merged.tone, TONES, 'Tone'); oneOf(merged.answer_length, LENGTHS, 'Answer length'); oneOf(merged.language, LANGUAGES, 'Language'); oneOf(merged.search_mode, MODES, 'Search mode'); oneOf(merged.formatting, ['RICH', 'PLAIN'], 'Formatting');
    if (merged.tone === 'CUSTOM' && !merged.custom_instruction?.trim())
        throw new Error('Describe the custom tone.');
    const models = await catalogService.models();
    const allowed = (name?: string | null) => models.some(model => model.name === name || model.id === name);
    if (!allowed(merged.model_name))
        throw new Error('Choose a primary model from the list your administrator allows.');
    if (merged.fallback_model_name) {
        if (!allowed(merged.fallback_model_name)) throw new Error('The fallback model is not available.');
        if (merged.fallback_model_name === merged.model_name) throw new Error('The fallback model must differ from the primary model.');
    }
    if (existing) {
        if (input.embedding_model && input.embedding_model !== existing.embedding_model)
            throw new Error('The embedding model cannot be changed after the chatbot is created.');
        merged.embedding_model = existing.embedding_model ?? defaultSettings.embedding_model;
    }
    else if (!(await catalogService.embeddingModels()).some(model => model.name === (merged.embedding_model ?? defaultSettings.embedding_model)))
        throw new Error('Choose an available embedding model.');
    return { ...merged, ...applyAdvancedSettings(input, existing), embedding_model: merged.embedding_model ?? defaultSettings.embedding_model };
}
function details(data: ChatbotInput) {
    const name = required(data.name, 'Chatbot name');
    if (name.length > 50) throw new Error('Chatbot name must be 50 characters or fewer.');
    if ((data.description ?? '').length > 200) throw new Error('Description must be 200 characters or fewer.');
    const starters = (data.starter_questions ?? []).map(question => question.trim()).filter(Boolean);
    if (starters.length > 4) throw new Error('Add up to 4 starter questions.');
    return { name, starters };
}
const withStatus = (bot: Chatbot, status: ChatbotStatus): ChatbotInput => ({ name: bot.name, description: bot.description, status });
export interface ListOptions { search?: string; status?: ChatbotStatus; sort?: 'UPDATED' | 'NAME_ASC' | 'NAME_DESC' | 'MESSAGES' }
export const localChatbotService = {
    async list(options: ListOptions = {}) {
        const search = options.search?.trim().toLowerCase();
        const rows = accessibleBots().map(counts).filter(bot => (!search || bot.name.toLowerCase().includes(search)) && (!options.status || bot.status === options.status));
        const sorters = { UPDATED: (a: Chatbot, b: Chatbot) => b.updated_at.localeCompare(a.updated_at), NAME_ASC: (a: Chatbot, b: Chatbot) => a.name.localeCompare(b.name),
            NAME_DESC: (a: Chatbot, b: Chatbot) => b.name.localeCompare(a.name), MESSAGES: (a: Chatbot, b: Chatbot) => (b.message_count ?? 0) - (a.message_count ?? 0) };
        return rows.sort(sorters[options.sort ?? 'UPDATED']);
    },
    async dashboard() { const bots = await this.list(); return { total_chatbots: bots.length, total_documents: bots.reduce((sum, b) => sum + b.document_count, 0), ready_documents: bots.reduce((sum, b) => sum + b.ready_count, 0), failed_documents: bots.reduce((sum, b) => sum + b.failed_count, 0), recent_chatbots: bots.slice(0, 6) }; },
    async get(botId: string) { return counts(requireBot(botId)); },
    async checklist(botId: string) { return publishChecklist(requireBot(botId)); },
    async create(data: ChatbotInput) {
        await delay();
        const user = currentAccount();
        assertCanCreateBot(user);
        const { name, starters } = details(data);
        const date = now();
        // New chatbots always start as DRAFT; they become PENDING when a document is indexed.
        const bot: Chatbot = { ...data, id: id('bot'), ownerId: user.id, organization_id: user.organization_id, name, starter_questions: starters, description: data.description ?? '',
            status: 'DRAFT', settings: await resolveSettings(data.settings), created_at: date, updated_at: date, document_count: 0, ready_count: 0, failed_count: 0 };
        chatbotRepository.put(bot);
        warnIfNearLimit(user);
        toast('Chatbot created.');
        return bot;
    },
    async update(botId: string, data: ChatbotInput) {
        await delay();
        const bot = requireBot(botId);
        const { name, starters } = details(data);
        const settings = await resolveSettings(data.settings, bot.settings);
        let status = bot.status;
        if (data.status && data.status !== bot.status) {
            assertTransition(bot, data.status);
            status = data.status;
        }
        const updated = { ...bot, ...data, name, starter_questions: data.starter_questions === undefined ? bot.starter_questions : starters, description: data.description ?? bot.description,
            status, settings, updated_at: now() };
        chatbotRepository.put(updated);
        toast('Settings saved.');
        return counts(updated);
    },
    async publish(botId: string) { return this.update(botId, withStatus(requireBot(botId), 'ACTIVE')); },
    async pause(botId: string) { return this.update(botId, withStatus(requireBot(botId), 'PAUSED')); },
    async resume(botId: string) { return this.update(botId, withStatus(requireBot(botId), 'ACTIVE')); },
    async duplicate(botId: string) { const bot = requireBot(botId); return this.create({ name: `${bot.name} (copy)`.slice(0, 50), description: bot.description, avatar: bot.avatar, settings: bot.settings, starter_questions: bot.starter_questions }); },
    /** Deleting requires retyping the chatbot name; guest channels then report that it is no longer available. */
    async delete(botId: string, confirmName: string) {
        await delay();
        const bot = requireBot(botId);
        if (confirmName.trim() !== bot.name)
            throw new ApiError(400, 'Type the chatbot name exactly to confirm deletion.');
        await deleteBotData(botId);
        toast('Chatbot deleted.');
    },
};
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const chatbotService: typeof localChatbotService = apiMode ? apiChatbotService : localChatbotService;
