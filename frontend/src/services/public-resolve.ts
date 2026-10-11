import { channelRepository } from '@/repositories/channel.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { userRepository } from '@/repositories/auth.repository';
import { tombstoneRepository } from '@/repositories/v5.repository';
import { seedDemo } from '@/mocks/seed';
import { slug } from '@/utils/demo';
import type { Channel } from './channel.service';
export const GUEST_MESSAGES = {
    deleted: 'This chatbot is no longer available!',
    paused: 'This assistant is temporarily unavailable',
    disabled: 'This chatbot has been disabled',
    unpublished: 'This chatbot has not been published yet.',
    linkOff: 'This link is disabled or no longer valid in this browser.',
    unavailable: 'This chatbot is unavailable.',
    expired: 'This public link has expired.',
    domain: 'This widget is not allowed on this website.',
} as const;
export function hostAllowed(domains: string[], host: string) {
    const name = host.toLowerCase().split(':')[0];
    return domains.some(domain => { const allowed = domain.toLowerCase().split(':')[0]; return name === allowed || name.endsWith(`.${allowed}`); });
}
/** Finds the chatbot behind a public token and enforces every guest-visible availability rule. */
export function resolveChannel(kind: string, token: string, botSlug?: string, referrerHost?: string) {
    seedDemo();
    if (tombstoneRepository.all().some(row => row.id === token))
        throw new Error(GUEST_MESSAGES.deleted);
    const channel = channelRepository.all().find((c: Channel) => c.public_id === token && c.channel === (kind === 'share' ? 'PUBLIC_LINK' : 'WEB_WIDGET'));
    if (!channel || !channel.enabled)
        throw new Error(GUEST_MESSAGES.linkOff);
    const bot = chatbotRepository.all().find(b => b.id === channel.chatbot_id);
    if (!bot)
        throw new Error(GUEST_MESSAGES.deleted);
    if ((botSlug && slug(bot.name) !== botSlug) || userRepository.all().find(u => u.id === bot.ownerId)?.status !== 'ACTIVE')
        throw new Error(GUEST_MESSAGES.unavailable);
    if (bot.status === 'DISABLED') throw new Error(GUEST_MESSAGES.disabled);
    if (bot.status === 'PAUSED') throw new Error(GUEST_MESSAGES.paused);
    if (bot.status !== 'ACTIVE') throw new Error(GUEST_MESSAGES.unpublished);
    if (channel.settings.expires && Date.parse(channel.settings.expires) < Date.now())
        throw new Error(GUEST_MESSAGES.expired);
    if (kind !== 'share' && referrerHost && channel.settings.domains.length && !hostAllowed(channel.settings.domains, referrerHost))
        throw new Error(GUEST_MESSAGES.domain);
    return { bot, channel };
}
