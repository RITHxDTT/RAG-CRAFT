import { resolveChannel } from './public-resolve';
import { mockAnswer, recordEvent } from './conversation.service';
import { rememberGuestAnswer } from './feedback.service';
import { delay, id } from '@/utils/demo';
export interface PublicBot {
    name: string;
    description: string;
    starter_questions: string[];
    welcome: string;
    color: string;
    avatar?: string;
    passwordRequired: boolean;
    icon: string;
    position: string;
}
export const publicChatService = {
    async metadata(kind: string, token: string, botSlug?: string, referrerHost?: string) { const { bot, channel } = resolveChannel(kind, token, botSlug, referrerHost); return { name: channel.settings.title || bot.name, description: bot.description, starter_questions: channel.settings.starters, welcome: channel.settings.welcome, color: channel.settings.color, avatar: bot.avatar, passwordRequired: !!channel.settings.password, icon: channel.settings.icon, position: channel.settings.position }; },
    async verify(kind: string, token: string, password: string) { await delay(); const { channel } = resolveChannel(kind, token); if (channel.settings.password !== password)
        throw new Error('Incorrect demo link password.'); return true; },
    async ask(kind: string, token: string, question: string, conversation_id?: string, session_token?: string, password = '', referrerHost?: string) {
        await delay(600);
        const { bot, channel } = resolveChannel(kind, token, undefined, referrerHost);
        if (channel.settings.password && channel.settings.password !== password)
            throw new Error('Enter the public link password.');
        if (!question.trim())
            throw new Error('Enter a question.');
        const result = mockAnswer(bot, question);
        const sessionId = conversation_id || id('guest'), guestId = session_token || id('session'), messageId = id('message');
        // Guest context lives in the page state; no guest conversation history is persisted. Only counters are kept for analytics.
        recordEvent(bot, channel.channel, { session_id: sessionId, guest_id: guestId, question: question.trim(), unanswered: result.unanswered, tokens: result.tokens });
        rememberGuestAnswer(messageId, { botId: bot.id, channel: channel.channel, question: question.trim(), answer: result.answer });
        return { answer: result.answer, sources: result.sources, conversation_id: sessionId, session_token: guestId, user_message_id: id('message'), message_id: messageId };
    },
};
