import { conversationRepository, analyticsRepository } from '@/repositories/conversation.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { id, now } from '@/utils/demo';
import { NOT_FOUND_MESSAGE, replyLanguage } from '@/utils/language';
import type { Chatbot } from '@/types/chatbot';
import type { AnalyticsEvent } from '@/types/demo';
import type { Answer, Conversation, Source } from '@/types/playground';
/** Text the earlier demo stored as the default fallback; treated as "not customised". */
const LEGACY_FALLBACKS = ['I do not have that information.', 'I do not have that information in this demo knowledge base.'];
export type MockAnswer = Pick<Answer, 'answer' | 'sources'> & { unanswered: boolean; tokens: number };
const estimateTokens = (text: string) => Math.max(1, Math.ceil(text.length / 4));
// Simulated: matches a few demo topics and document names. No retrieval or generation takes place.
function matchesKnowledge(question: string, docNames: string[]) {
    const q = question.toLowerCase();
    if (/leave|refund|start/.test(q)) return true;
    const words = q.split(/[^\p{L}\p{N}]+/u).filter(word => word.length > 3);
    return docNames.some(name => words.some(word => name.toLowerCase().includes(word)));
}
export function mockAnswer(bot: Chatbot, question: string, model?: string): MockAnswer {
    const docs = knowledgeRepository.all().filter(doc => doc.chatbot_id === bot.id && doc.status === 'READY');
    const q = question.toLowerCase();
    const documentsOnly = bot.settings.answer_from_documents_only !== false;
    if (!docs.length || (documentsOnly && !matchesKnowledge(question, docs.map(doc => doc.name)))) {
        // Not in the knowledge base: localised fixed reply, no citations, counted as unanswered.
        const custom = bot.settings.fallback_message?.trim();
        const reply = custom && !LEGACY_FALLBACKS.includes(custom) ? custom : NOT_FOUND_MESSAGE[replyLanguage(bot.settings.language, question)];
        const answer = `${reply}\n\nDemo response · ${model || bot.settings.model_name}`;
        return { answer, sources: [], unanswered: true, tokens: estimateTokens(question + answer) };
    }
    const topic = q.includes('leave') ? 'Employees receive 18 days of annual leave in this example policy.' : q.includes('refund') ? 'This example refund policy allows returns within 30 days with proof of purchase.' : q.includes('start') ? 'Start by creating a chatbot, adding knowledge sources, testing in the Playground, and enabling a channel.' : `This is a simulated answer to “${question}”. Review your configured sources and instructions to explore the intended workflow.`;
    const topK = Math.max(1, Math.min(bot.settings.top_k || 5, 20));
    const sources: Source[] = bot.settings.show_citations === false ? [] : docs.slice(0, Math.min(2, topK)).map((doc, index) => ({ chunk_id: `mock_${doc.id}`, document_id: doc.id, document_name: doc.name, page_number: doc.file_type === 'PDF' ? 12 : null, chunk_index: index, excerpt: 'Illustrative demo excerpt. This text has not been extracted from the uploaded file.', score: 0.9 - index * 0.05, sheet_name: doc.file_type === 'XLSX' ? 'Sheet1' : null, row_number: doc.file_type === 'XLSX' ? 4 : null, url: doc.url }));
    const text = `${bot.settings.tone === 'FRIENDLY' ? 'Happy to help! ' : ''}${topic}${sources.length ? ' [1]' : ''}${bot.settings.answer_length === 'DETAILED' ? ' This example illustrates the response format; no retrieval or AI generation takes place.' : ''}`;
    const answer = `${text}\n\nDemo response · ${model || bot.settings.model_name}`;
    return { answer, sources, unanswered: false, tokens: estimateTokens(question + answer) };
}
export function recordEvent(bot: Chatbot, channel: string, extra: Partial<Pick<AnalyticsEvent, 'session_id' | 'guest_id' | 'question' | 'unanswered' | 'tokens' | 'model'>> = {}) {
    analyticsRepository.put({ id: id('event'), ownerId: bot.ownerId, chatbot_id: bot.id, channel, created_at: now(), ...extra });
}
export function saveAnswer(bot: Chatbot, question: string, conversationId: string | null, result: MockAnswer, channel: Conversation['channel'] = 'PLAYGROUND', model?: string): Answer {
    const date = now();
    const existing = conversationId ? conversationRepository.all().find(c => c.id === conversationId && c.chatbot_id === bot.id && c.channel === channel) : undefined;
    if (conversationId && !existing)
        throw new Error('Conversation not found. Start a new conversation.');
    const cid = existing?.id ?? id('conversation');
    const userId = id('message');
    const messageId = id('message');
    conversationRepository.put({ id: cid, ownerId: bot.ownerId, chatbot_id: bot.id, channel, title: existing?.title ?? question.slice(0, 80), created_at: existing?.created_at ?? date, updated_at: date, messages: [...(existing?.messages ?? []), { id: userId, role: 'USER', content: question, sources: [] }, { id: messageId, role: 'ASSISTANT', content: result.answer, sources: result.sources }] });
    recordEvent(bot, channel, { session_id: cid, question, unanswered: result.unanswered, tokens: result.tokens, model: model || bot.settings.model_name });
    return { answer: result.answer, sources: result.sources, conversation_id: cid, user_message_id: userId, message_id: messageId };
}
