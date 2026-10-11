import { get, post } from '../http';
import { userRepository } from '@/repositories/auth.repository';
import type { localAppealService } from '../appeal.service';
import type { Appeal } from '@/types/v5';
/* eslint-disable @typescript-eslint/no-explicit-any -- raw server JSON */
type Dto = Record<string, any>;
const mapAppeal = (dto: Dto): Appeal => ({ id: dto.id, chatbot_id: dto.chatbot_id, chatbot_name: dto.chatbot_name ?? '', ownerId: dto.owner_id, owner_email: dto.owner_email ?? '', message: dto.message,
    status: dto.status, decision_reason: dto.decision_reason ?? null, decided_by: null, created_at: dto.created_at, decided_at: dto.decided_at ?? null });
/** Appeals live in chatbot-service (craftrag_db.chatbot). Approving or rejecting also writes the audit log. */
export const apiAppealService: typeof localAppealService = {
    async submit(botId, message) { return mapAppeal(await post<Dto>(`/api/chatbots/${botId}/appeals`, { message })); },
    async forBot(botId) { return (await get<Dto[]>(`/api/chatbots/${botId}/appeals`)).map(mapAppeal); },
    async list(status) { return (await get<Dto[]>('/api/admin/appeals', { status })).map(mapAppeal); },
    async pendingCount() { return (await get<{ count: number }>('/api/admin/appeals/pending-count')).count; },
    async approve(appealId, reason = 'Appeal approved.') { return mapAppeal(await post<Dto>(`/api/admin/appeals/${appealId}/approve`, { reason })); },
    async reject(appealId, reason) { return mapAppeal(await post<Dto>(`/api/admin/appeals/${appealId}/reject`, { reason })); },
    ownerEmail: ownerId => userRepository.all().find(user => user.id === ownerId)?.email ?? '',
};
