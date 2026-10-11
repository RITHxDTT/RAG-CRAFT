import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { ApiError } from './api';
import { now } from '@/utils/demo';
import type { Chatbot, ChatbotStatus, PublishChecklist } from '@/types/chatbot';
export function publishChecklist(bot: Chatbot): PublishChecklist {
    const hasDocument = knowledgeRepository.all().some(doc => doc.chatbot_id === bot.id && doc.status === 'READY');
    const modelSelected = !!bot.settings.model_name?.trim();
    const channelReady = channelRepository.all().some(channel => channel.chatbot_id === bot.id);
    return { hasDocument, modelSelected, channelReady, ready: hasDocument && modelSelected && channelReady };
}
/** DRAFT becomes PENDING once a document is READY, and falls back to DRAFT if none remain. */
export function syncKnowledgeStatus(botId: string) {
    const bot = chatbotRepository.all().find(row => row.id === botId);
    if (!bot) return;
    const ready = knowledgeRepository.all().some(doc => doc.chatbot_id === botId && doc.status === 'READY');
    const next: ChatbotStatus | null = bot.status === 'DRAFT' && ready ? 'PENDING' : bot.status === 'PENDING' && !ready ? 'DRAFT' : null;
    if (next)
        chatbotRepository.put({ ...bot, status: next, updated_at: now() });
}
/** Owner-initiated moves. DISABLED is set and lifted by admins only; DRAFT/PENDING follow the knowledge base. */
const ALLOWED: Record<ChatbotStatus, ChatbotStatus[]> = { DRAFT: [], PENDING: ['ACTIVE'], ACTIVE: ['PAUSED'], PAUSED: ['ACTIVE'], DISABLED: [] };
export function assertTransition(bot: Chatbot, next: ChatbotStatus) {
    if (bot.status === 'DISABLED')
        throw new ApiError(403, 'This chatbot was disabled by an administrator. Submit an appeal to request a review.');
    if (!ALLOWED[bot.status].includes(next))
        throw new ApiError(409, next === 'ACTIVE' && bot.status === 'DRAFT' ? 'Add at least one document and wait for indexing before publishing.' : `A ${bot.status.toLowerCase()} chatbot cannot move to ${next.toLowerCase()}.`);
    if (next === 'ACTIVE') {
        const checklist = publishChecklist(bot);
        const missing = [!checklist.hasDocument && 'at least one ready document', !checklist.modelSelected && 'a model', !checklist.channelReady && 'one channel'].filter(Boolean);
        if (missing.length)
            throw new ApiError(409, `Before publishing you need ${missing.join(', ')}.`);
    }
}
