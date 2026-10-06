import { repository } from './repository';
import { KEYS } from '@/storage/keys';
import type { Channel } from '@/services/channel.service';
export const channelRepository = repository<Channel>(KEYS.channels);
