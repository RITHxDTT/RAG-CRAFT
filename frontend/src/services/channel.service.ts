import { apiMode } from '@/config/data-source';
import { apiChannelService } from './api/channel.api';
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
    /** Why the channel is in ERROR (for example an invalid Telegram token). */
    error_reason?: string | null;
    settings: ChannelSettings;
    created_at: string;
    updated_at: string;
    messages_this_week?: number;
}
// Telegram bot tokens look like 123456789:AA... ; only the shape is checked and the token itself is never stored.
const TELEGRAM_TOKEN = /^\d{6,}:[A-Za-z0-9_-]{30,}$/;
export function normalizeDomain(value: string) { return value.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, ''); }
function row(bot: string, channelId: string) { requireBot(bot); const channel = channelRepository.all().find(c => c.id === channelId && c.chatbot_id === bot); if (!channel)
    throw new Error('Channel not found.'); return channel; }
function attribute(value: string) { return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;"); }
function decorate(channel: Channel): Channel {
    const bot = requireBot(channel.chatbot_id);
    const origin = window.location.origin;
    const url = channel.channel === 'TELEGRAM' ? null : channel.channel === 'PUBLIC_LINK' ? `${origin}/share/${slug(bot.name)}/${channel.public_id}` : `${origin}/widget/${channel.public_id}`;
    return { ...channel, status: channel.error_reason ? 'ERROR' : channel.enabled ? 'LIVE' : 'OFF', url, embed_code: channel.channel === 'WEB_WIDGET' ? `<script src="${origin}/widget.js" data-widget-id="${channel.public_id}" data-color="${attribute(channel.settings.color)}" data-position="${attribute(channel.settings.position)}" data-icon="${attribute(channel.settings.icon)}"></script>` : null,
        messages_this_week: analyticsRepository.all().filter(e => e.chatbot_id === bot.id && e.channel === channel.channel && Date.parse(e.created_at) > Date.now() - 7 * 86400000).length };
}
export const localChannelService = {
    async list(bot: string) { requireBot(bot); return channelRepository.all().filter(c => c.chatbot_id === bot).map(decorate); },
    async create(botId: string, channel: Channel['channel'], token?: string) {
        const bot = requireBot(botId);
        await delay();
        if (channel === 'TELEGRAM' && !token?.trim())
            throw new Error('Enter a bot token.');
        if (channelRepository.all().some(c => c.chatbot_id === botId && c.channel === channel))
            throw new Error('This channel already exists.');
        const date = now();
        const value: Channel = { id: id('channel'), ownerId: bot.ownerId, chatbot_id: botId, channel, public_id: crypto.randomUUID(), enabled: channel !== 'WEB_WIDGET', status: channel === 'WEB_WIDGET' ? 'OFF' : 'LIVE', url: null, embed_code: null, telegram_username: channel === 'TELEGRAM' ? 'ragcraft_demo_bot' : null, token_mask: channel === 'TELEGRAM' ? 'Demo token ••••' : null, error_reason: null, created_at: date, updated_at: date,
            settings: { title: bot.name, welcome: bot.settings.welcome_message ?? 'Hello!', color: '#0d9488', position: 'BOTTOM_RIGHT', icon: '💬', domains: [], starters: bot.starter_questions ?? [], password: '', expires: '' } };
        if (channel === 'TELEGRAM' && !TELEGRAM_TOKEN.test(token!.trim())) {
            Object.assign(value, { enabled: false, error_reason: 'The bot token is not valid. Copy it again from @BotFather.', telegram_username: null, token_mask: null });
        }
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
        const domains = Array.from(new Set(settings.domains.map(normalizeDomain).filter(Boolean)));
        if (domains.some(domain => !/^([a-z0-9-]+\.)*[a-z0-9-]+(:\d+)?$/i.test(domain)))
            throw new Error('Use domain names without paths, such as example.com.');
        if ((settings.starters ?? []).filter(question => question.trim()).length > 4)
            throw new Error('Add up to 4 starter questions.');
        const saved = { ...channel, settings: { ...settings, domains }, updated_at: now() };
        channelRepository.put(saved);
        toast('Channel settings saved.');
        return decorate(saved);
    },
    async toggle(bot: string, value: Channel) { await delay(); const channel = row(bot, value.id); if (!channel.enabled && channel.error_reason)
        throw new Error(channel.error_reason); const updated = { ...channel, enabled: !channel.enabled, status: channel.enabled ? 'OFF' : 'LIVE', updated_at: now() }; channelRepository.put(updated); toast('Channel updated.'); return decorate(updated); },
    async regenerate(bot: string, channelId: string) { await delay(); const channel = row(bot, channelId); const updated = { ...channel, public_id: crypto.randomUUID(), updated_at: now() }; channelRepository.put(updated); toast('Link regenerated. Previous token invalidated.'); return decorate(updated); },
    async test(bot: string, channelId: string) { await delay(600); return decorate(row(bot, channelId)); },
    async connect(bot: string, channelId: string, token: string) { if (!token.trim())
        throw new Error('Enter a demo token.'); await delay(650); const channel = row(bot, channelId); if (!TELEGRAM_TOKEN.test(token.trim())) {
        const failed = { ...channel, enabled: false, status: 'ERROR', error_reason: 'The bot token is not valid. Copy it again from @BotFather.', token_mask: null, telegram_username: null, updated_at: now() };
        channelRepository.put(failed);
        return decorate(failed);
    } const updated = { ...channel, enabled: true, status: 'LIVE', error_reason: null, token_mask: 'Demo token ••••', telegram_username: 'ragcraft_demo_bot', updated_at: now() }; channelRepository.put(updated); toast('Telegram connected (simulation).'); return decorate(updated); },
    async disconnect(bot: string, channelId: string) { await delay(); const channel = row(bot, channelId); const updated = { ...channel, enabled: false, status: 'OFF', error_reason: null, token_mask: null, telegram_username: null, updated_at: now() }; channelRepository.put(updated); toast('Telegram disconnected.'); return decorate(updated); },
    async remove(bot: string, channelId: string) { row(bot, channelId); await delay(); channelRepository.remove(channelId); },
};
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const channelService: typeof localChannelService = apiMode ? apiChannelService : localChannelService;
