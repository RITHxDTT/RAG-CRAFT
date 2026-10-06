import { conversationRepository, analyticsRepository } from '@/repositories/conversation.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { id, now } from '@/utils/demo';
import type { Chatbot } from '@/types/chatbot';
import type { Answer, Conversation, Source } from '@/types/playground';
export function mockAnswer(bot: Chatbot, question: string, model?: string): Pick<Answer, 'answer' | 'sources'> {
    const docs = knowledgeRepository.all().filter(doc => doc.chatbot_id === bot.id && doc.status === 'READY');
    const q = question.toLowerCase();
    const topic = q.includes('leave') ? 'Employees receive 18 days of annual leave in this example policy.' : q.includes('refund') ? 'This example refund policy allows returns within 30 days with proof of purchase.' : q.includes('start') ? 'Start by creating a chatbot, adding knowledge sources, testing in the Playground, and enabling a channel.' : `This is a simulated answer to “${question}”. Review your configured sources and instructions to explore the intended workflow.`;
    const sources: Source[] = bot.settings.show_citations === false ? [] : docs.slice(0, 2).map((doc, index) => ({ chunk_id: `mock_${doc.id}`, document_id: doc.id, document_name: doc.name, page_number: doc.file_type === 'PDF' ? 12 : null, chunk_index: index, excerpt: 'Illustrative demo excerpt. This text has not been extracted from the uploaded file.', score: 0.9 - index * 0.05, sheet_name: doc.file_type === 'XLSX' ? 'Sheet1' : null, row_number: doc.file_type === 'XLSX' ? 4 : null, url: doc.url }));
    const answer = docs.length ? `${bot.settings.tone === 'FRIENDLY' ? 'Happy to help! ' : ''}${topic}${sources.length ? ' [1]' : ''}${bot.settings.answer_length === 'LONG' ? ' This example illustrates the response format; no retrieval or AI generation takes place.' : ''}` : (bot.settings.fallback_message || 'Add a ready knowledge source to explore simulated answers.');
    return { answer: `${answer}\n\nDemo response · ${model || bot.settings.model_name}`, sources };
}
export function recordEvent(bot: Chatbot, channel: string) { analyticsRepository.put({ id: id('event'), ownerId: bot.ownerId, chatbot_id: bot.id, channel, created_at: now() }); }
export function saveAnswer(bot: Chatbot, question: string, conversationId: string | null, result: Pick<Answer, 'answer' | 'sources'>, channel: Conversation['channel'] = 'PLAYGROUND'): Answer {
    const date = now();
    const existing = conversationId ? conversationRepository.all().find(c => c.id === conversationId && c.chatbot_id === bot.id && c.channel === channel) : undefined;
    if (conversationId && !existing)
        throw new Error('Conversation not found. Start a new conversation.');
    const cid = existing?.id ?? id('conversation');
    const userId = id('message');
    const messageId = id('message');
    conversationRepository.put({ id: cid, ownerId: bot.ownerId, chatbot_id: bot.id, channel, title: existing?.title ?? question.slice(0, 80), created_at: existing?.created_at ?? date, updated_at: date, messages: [...(existing?.messages ?? []), { id: userId, role: 'USER', content: question, sources: [] }, { id: messageId, role: 'ASSISTANT', content: result.answer, sources: result.sources }] });
    recordEvent(bot, channel);
    return { ...result, conversation_id: cid, user_message_id: userId, message_id: messageId };
}
