import { repository } from './repository';
import { KEYS } from '@/storage/keys';
import type { Chatbot } from '@/types/chatbot';
export const chatbotRepository = repository<Chatbot>(KEYS.chatbots);
