import { conversationRepository } from '@/repositories/conversation.repository';
import { requireBot } from './access';
import { mockAnswer, saveAnswer } from './conversation.service';
import { delay, toast } from '@/utils/demo';
export const playgroundService = {
    async ask(botId: string, question: string, conversationId: string | null, model?: string) { const bot = requireBot(botId); if (!question.trim())
        throw new Error('Enter a question.'); await delay(650); requireBot(botId); return saveAnswer(bot, question, conversationId, mockAnswer(bot, question, model), 'PLAYGROUND', model); },
    async compare(botId: string, question: string, models: string[]) { const bot = requireBot(botId); if (!question.trim())
        throw new Error('Enter a question.'); await delay(700); return models.map(model => saveAnswer(bot, question, null, mockAnswer(bot, question, model), 'PLAYGROUND', model)); },
    async list(botId: string) { requireBot(botId); return conversationRepository.all().filter(c => c.chatbot_id === botId).sort((a, b) => b.updated_at.localeCompare(a.updated_at)); },
    async get(botId: string, cid: string) { requireBot(botId); const row = conversationRepository.all().find(c => c.id === cid && c.chatbot_id === botId); if (!row)
        throw new Error('Conversation not found.'); return row; },
    async delete(botId: string, cid: string) { await this.get(botId, cid); await delay(); conversationRepository.remove(cid); toast('Conversation deleted.'); },
};
