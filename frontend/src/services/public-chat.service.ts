import { channelRepository } from '@/repositories/channel.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { userRepository } from '@/repositories/auth.repository';
import { seedDemo } from '@/mocks/seed';
import { mockAnswer, recordEvent } from './conversation.service';
import { delay, id, slug } from '@/utils/demo';
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
function resolve(kind: string, token: string, botSlug?: string) {
    seedDemo();
    const channel = channelRepository.all().find(c => c.public_id === token && c.channel === (kind === 'share' ? 'PUBLIC_LINK' : 'WEB_WIDGET'));
    if (!channel || !channel.enabled)
        throw new Error('This link is disabled or no longer valid in this browser.');
    const bot = chatbotRepository.all().find(b => b.id === channel.chatbot_id);
    if (!bot || (botSlug && slug(bot.name) !== botSlug) || !userRepository.all().find(u => u.id === bot.ownerId)?.is_active)
        throw new Error('This chatbot is unavailable.');
    if (bot.status !== 'ACTIVE')
        throw new Error('This chatbot has not been published as Active.');
    if (channel.settings.expires && Date.parse(channel.settings.expires) < Date.now())
        throw new Error('This public link has expired.');
    return { bot, channel };
}
export const publicChatService = {
    async metadata(kind: string, token: string, botSlug?: string) { const { bot, channel } = resolve(kind, token, botSlug); return { name: channel.settings.title || bot.name, description: bot.description, starter_questions: channel.settings.starters, welcome: channel.settings.welcome, color: channel.settings.color, avatar: bot.avatar, passwordRequired: !!channel.settings.password, icon: channel.settings.icon, position: channel.settings.position }; },
    async verify(kind: string, token: string, password: string) { await delay(); const { channel } = resolve(kind, token); if (channel.settings.password !== password)
        throw new Error('Incorrect demo link password.'); return true; },
    async ask(kind: string, token: string, question: string, conversation_id?: string, session_token?: string, password = '') {
        await delay(600);
        const { bot, channel } = resolve(kind, token);
        if (channel.settings.password && channel.settings.password !== password)
            throw new Error('Enter the public link password.');
        if (!question.trim())
            throw new Error('Enter a question.');
        recordEvent(bot, channel.channel);
        // Guest context lives in the page state; no guest conversation history is persisted.
        return { ...mockAnswer(bot, question), conversation_id: conversation_id || id('guest'), session_token: session_token || id('session'), user_message_id: id('message'), message_id: id('message') };
    },
};
