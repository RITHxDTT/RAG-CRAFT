import { del, get, patch, post } from '../http';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { currentAccount } from '../access';
import { botPayload, mapBot, mirrorBots } from './mappers';
import type { localChatbotService } from '../chatbot.service';
import type { Chatbot } from '@/types/chatbot';
/* eslint-disable @typescript-eslint/no-explicit-any -- raw server JSON */
type Dto = Record<string, any>;
function mirrorOne(bot: Chatbot) { mirrorBots([bot]); return bot; }
/** Chatbots live in chatbot-service (craftrag_db.chatbot). Every read refreshes the local cache used by the screens not yet on the database. */
export const apiChatbotService: typeof localChatbotService = {
    async list(options = {}) {
        const rows = (await get<Dto[]>('/api/chatbots', { search: options.search, status: options.status, sort: options.sort })).map(mapBot);
        // Only an unfiltered list can tell which cached rows have disappeared on the server.
        if (!options.search && !options.status) mirrorBots(rows, currentAccount().role === 'ADMIN' ? undefined : currentAccount().id);
        else mirrorBots(rows);
        return rows;
    },
    async dashboard() {
        const dto = await get<Dto>('/api/dashboard');
        const recent = (dto.recent_chatbots ?? []).map(mapBot);
        mirrorBots(recent);
        return { total_chatbots: dto.total_chatbots, total_documents: dto.total_documents, ready_documents: dto.ready_documents, failed_documents: dto.failed_documents, recent_chatbots: recent };
    },
    async get(botId) { return mirrorOne(mapBot(await get<Dto>(`/api/chatbots/${botId}`))); },
    async checklist(botId) {
        const dto = await get<Dto>(`/api/chatbots/${botId}/checklist`);
        return { hasDocument: dto.has_document, modelSelected: dto.model_selected, channelReady: dto.channel_ready, ready: dto.ready };
    },
    async create(data) { return mirrorOne(mapBot(await post<Dto>('/api/chatbots', botPayload(data)))); },
    async update(botId, data) { return mirrorOne(mapBot(await patch<Dto>(`/api/chatbots/${botId}`, botPayload(data)))); },
    async publish(botId) { return mirrorOne(mapBot(await post<Dto>(`/api/chatbots/${botId}/publish`))); },
    async pause(botId) { return mirrorOne(mapBot(await post<Dto>(`/api/chatbots/${botId}/pause`))); },
    async resume(botId) { return mirrorOne(mapBot(await post<Dto>(`/api/chatbots/${botId}/resume`))); },
    async duplicate(botId) { return mirrorOne(mapBot(await post<Dto>(`/api/chatbots/${botId}/duplicate`))); },
    async delete(botId, confirmName) {
        await del(`/api/chatbots/${botId}`, { confirm_name: confirmName });
        chatbotRepository.saveQuiet(chatbotRepository.all().filter(bot => bot.id !== botId));
        knowledgeRepository.saveQuiet(knowledgeRepository.all().filter(doc => doc.chatbot_id !== botId));
        channelRepository.saveQuiet(channelRepository.all().filter(row => row.chatbot_id !== botId));
    },
};
