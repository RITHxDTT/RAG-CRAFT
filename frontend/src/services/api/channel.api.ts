import { del, get, http, patch, post } from '../http';
import { ApiError } from '../api';
import { channelRepository } from '@/repositories/channel.repository';
import { mapChannel, mirrorChannels } from './mappers';
import type { localChannelService, Channel } from '../channel.service';
/* eslint-disable @typescript-eslint/no-explicit-any -- raw server JSON */
type Dto = Record<string, any>;
async function refresh(botId: string) {
    const channels = (await get<Dto[]>(`/api/chatbots/${botId}/channels`)).map(mapChannel);
    mirrorChannels(botId, channels);
    return channels;
}
function keep(channel: Channel) {
    channelRepository.saveQuiet([...channelRepository.all().filter(row => row.id !== channel.id), channel]);
    return channel;
}
/** Share link, web widget and Telegram live in channel-service (craftrag_db.channel). */
export const apiChannelService: typeof localChannelService = {
    list: refresh,
    async create(botId, channel, token) { return keep(mapChannel(await post<Dto>(`/api/chatbots/${botId}/channels`, { channel, token }))); },
    async save(botId, channelId, settings) { return keep(mapChannel(await http<Dto>('PUT', `/api/chatbots/${botId}/channels/${channelId}/settings`, { body: settings }))); },
    async toggle(botId, value) { return keep(mapChannel(await patch<Dto>(`/api/chatbots/${botId}/channels/${value.id}`, { enabled: !value.enabled }))); },
    async regenerate(botId, channelId) { return keep(mapChannel(await post<Dto>(`/api/chatbots/${botId}/channels/${channelId}/regenerate`))); },
    async test(botId, channelId) { return keep(mapChannel(await post<Dto>(`/api/chatbots/${botId}/channels/${channelId}/test`))); },
    async connect(botId, channelId, token) { return keep(mapChannel(await post<Dto>(`/api/chatbots/${botId}/channels/${channelId}/connect`, { token }))); },
    async disconnect() { throw new ApiError(501, 'Disconnecting Telegram is not available with the database yet. Remove the channel instead.', 'NOT_IMPLEMENTED'); },
    async remove(botId, channelId) {
        await del(`/api/chatbots/${botId}/channels/${channelId}`);
        channelRepository.saveQuiet(channelRepository.all().filter(row => row.id !== channelId));
    },
};
