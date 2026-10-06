import { channelRepository } from '@/repositories/channel.repository';
import { analyticsRepository } from '@/repositories/conversation.repository';
import { requireBot } from './access';
import { delay, id, now, slug, toast } from '@/utils/demo';
export interface ChannelSettings {
    title: string;
    welcome: string;
    color: string;
    position: 'BOTTOM_RIGHT' | 'BOTTOM_LEFT';
    icon: string;
    domains: string[];
    starters: string[];
    password: string;
    expires: string;
}
export interface Channel {
    id: string;
    ownerId: string;
    chatbot_id: string;
    channel: 'PUBLIC_LINK' | 'WEB_WIDGET' | 'TELEGRAM';
    public_id: string;
    enabled: boolean;
    status: string;
    url: string | null;
    embed_code: string | null;
    telegram_username: string | null;
    token_mask: string | null;
    settings: ChannelSettings;
    created_at: string;
    updated_at: string;
    messages_this_week?: number;
}
function row(bot: string, channelId: string) { requireBot(bot); const channel = channelRepository.all().find(c => c.id === channelId && c.chatbot_id === bot); if (!channel)
    throw new Error('Channel not found.'); return channel; }
function attribute(value: string) { return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;"); }
function decorate(channel: Channel): Channel {
    const bot = requireBot(channel.chatbot_id);
    const origin = window.location.origin;
    const url = channel.channel === 'TELEGRAM' ? null : channel.channel === 'PUBLIC_LINK' ? `${origin}/share/${slug(bot.name)}/${channel.public_id}` : `${origin}/widget/${channel.public_id}`;
    return { ...channel, url, embed_code: channel.channel === 'WEB_WIDGET' ? `<script src="${origin}/widget.js" data-widget-id="${channel.public_id}" data-color="${attribute(channel.settings.color)}" data-position="${attribute(channel.settings.position)}" data-icon="${attribute(channel.settings.icon)}"></script>` : null,
        messages_this_week: analyticsRepository.all().filter(e => e.chatbot_id === bot.id && e.channel === channel.channel && Date.parse(e.created_at) > Date.now() - 7 * 86400000).length };
}
export const channelService = {
    async list(bot: string) { requireBot(bot); return channelRepository.all().filter(c => c.chatbot_id === bot).map(decorate); },
    async create(botId: string, channel: Channel['channel'], token?: string) {
        const bot = requireBot(botId);
        await delay();
        if (channel === 'TELEGRAM' && !token?.trim())
            throw new Error('Enter a demo bot token. Do not use a real secret.');
        if (channelRepository.all().some(c => c.chatbot_id === botId && c.channel === channel))
            throw new Error('This channel already exists.');
        const date = now();
        const value: Channel = { id: id('channel'), ownerId: bot.ownerId, chatbot_id: botId, channel, public_id: crypto.randomUUID(), enabled: channel !== 'WEB_WIDGET', status: channel === 'WEB_WIDGET' ? 'OFF' : 'LIVE', url: null, embed_code: null, telegram_username: channel === 'TELEGRAM' ? 'ragcraft_demo_bot' : null, token_mask: channel === 'TELEGRAM' ? 'Demo token ••••' : null, created_at: date, updated_at: date,
            settings: { title: bot.name, welcome: bot.settings.welcome_message ?? 'Hello!', color: '#0d9488', position: 'BOTTOM_RIGHT', icon: '💬', domains: [], starters: bot.starter_questions ?? [], password: '', expires: '' } };
        channelRepository.put(value);
        toast('Channel generated.');
        return decorate(value);
    },
    async save(bot: string, channelId: string, settings: ChannelSettings) {
        await delay();
        const channel = row(bot, channelId);
        if (!/^#[0-9a-f]{6}$/i.test(settings.color))
            throw new Error('Choose a valid color.');
        if (settings.expires && !Number.isFinite(Date.parse(settings.expires)))
            throw new Error('Enter a valid expiration date.');
        if (settings.domains.some(domain => !/^([a-z0-9-]+\.)*[a-z0-9-]+(:\d+)?$/i.test(domain)))
            throw new Error('Use domain names without paths, such as example.com.');
        channelRepository.put({ ...channel, settings, updated_at: now() });
        toast('Channel settings saved.');
        return decorate({ ...channel, settings });
    },
    async toggle(bot: string, value: Channel) { await delay(); const channel = row(bot, value.id); const updated = { ...channel, enabled: !channel.enabled, status: channel.enabled ? 'OFF' : 'LIVE', updated_at: now() }; channelRepository.put(updated); toast('Channel updated.'); return decorate(updated); },
    async regenerate(bot: string, channelId: string) { await delay(); const channel = row(bot, channelId); const updated = { ...channel, public_id: crypto.randomUUID(), updated_at: now() }; channelRepository.put(updated); toast('Link regenerated. Previous token invalidated.'); return decorate(updated); },
    async test(bot: string, channelId: string) { await delay(600); return decorate(row(bot, channelId)); },
    async connect(bot: string, channelId: string, token: string) { if (!token.trim())
        throw new Error('Enter a demo token.'); await delay(650); const channel = row(bot, channelId); const updated = { ...channel, enabled: true, status: 'LIVE', token_mask: 'Demo token ••••', telegram_username: 'ragcraft_demo_bot', updated_at: now() }; channelRepository.put(updated); toast('Telegram connected (simulation).'); return decorate(updated); },
    async remove(bot: string, channelId: string) { row(bot, channelId); await delay(); channelRepository.remove(channelId); },
};
