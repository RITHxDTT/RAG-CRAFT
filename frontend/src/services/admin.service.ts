import { userRepository } from '@/repositories/auth.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { conversationRepository, analyticsRepository } from '@/repositories/conversation.repository';
import { fileStorage } from '@/storage/indexed-db';
import { requireAdmin } from './access';
import { analyticsService } from './analytics.service';
import { delay, now, toast } from '@/utils/demo';
export interface UserRecord {
    id: string;
    full_name: string;
    email: string;
    role: string;
    is_active: boolean;
    chatbot_count: number;
    created_at: string;
    builtIn?: boolean;
}
export interface MonitoredBot {
    id: string;
    name: string;
    status: string;
    owner_email: string;
    owner_name: string;
    created_at: string;
    knowledge_count?: number;
    channel_count?: number;
}
export interface Usage {
    total_chatbots: number;
    total_documents: number;
    total_conversations: number;
    total_messages: number;
    total_users?: number;
    active_users?: number;
    messages_by_channel: Record<string, number>;
    active_chatbots?: number;
    total_knowledge?: number;
    total_channels?: number;
    active_channels?: number;
    messages_this_week?: number;
    messages_by_chatbot?: Record<string, number>;
}
function users(): UserRecord[] { requireAdmin(); return userRepository.all().map(u => ({ id: u.id, full_name: u.full_name, email: u.email, role: u.role, is_active: u.is_active, builtIn: u.builtIn, created_at: u.created_at, chatbot_count: chatbotRepository.all().filter(b => b.ownerId === u.id).length })); }
export const adminService = {
    async users(search = '', offset = 0) { return users().filter(u => `${u.full_name} ${u.email}`.toLowerCase().includes(search.toLowerCase())).slice(offset, offset + 100); },
    async user(uid: string) { const user = users().find(u => u.id === uid); if (!user)
        throw new Error('User not found.'); return user; },
    async status(uid: string, is_active: boolean) { requireAdmin(); await delay(); const user = userRepository.all().find(u => u.id === uid); if (!user)
        throw new Error('User not found.'); if (user.builtIn)
        throw new Error('Fixed demo accounts are protected.'); userRepository.put({ ...user, is_active, updated_at: now() }); return this.user(uid); },
    async remove(uid: string) { requireAdmin(); const user = userRepository.all().find(u => u.id === uid); if (!user || user.builtIn)
        throw new Error('Fixed demo accounts are protected.'); await delay(); const botIds = new Set(chatbotRepository.all().filter(b => b.ownerId === uid).map(b => b.id)); const docs = knowledgeRepository.all().filter(d => botIds.has(d.chatbot_id)); await Promise.all(docs.map(d => fileStorage.remove(d.id))); chatbotRepository.save(chatbotRepository.all().filter(b => b.ownerId !== uid)); knowledgeRepository.save(knowledgeRepository.all().filter(d => d.ownerId !== uid)); channelRepository.save(channelRepository.all().filter(c => c.ownerId !== uid)); conversationRepository.save(conversationRepository.all().filter(c => c.ownerId !== uid)); analyticsRepository.save(analyticsRepository.all().filter(e => e.ownerId !== uid)); userRepository.remove(uid); toast('Local account and its data deleted.'); },
    async bots(offset = 0) { requireAdmin(); return chatbotRepository.all().slice(offset, offset + 100).map(b => ({ id: b.id, name: b.name, status: b.status, owner_email: userRepository.all().find(u => u.id === b.ownerId)?.email ?? '', owner_name: userRepository.all().find(u => u.id === b.ownerId)?.full_name ?? '', created_at: b.created_at, knowledge_count: knowledgeRepository.all().filter(d => d.chatbot_id === b.id).length, channel_count: channelRepository.all().filter(c => c.chatbot_id === b.id).length })); },
    usage: analyticsService.usage,
};
