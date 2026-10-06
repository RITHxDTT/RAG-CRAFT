import { repository } from './repository';
import { KEYS } from '@/storage/keys';
import type { ConversationDetail } from '@/types/playground';
import type { AnalyticsEvent } from '@/types/demo';
export const conversationRepository = repository<ConversationDetail>(KEYS.conversations);
export const analyticsRepository = repository<AnalyticsEvent>(KEYS.analytics);
