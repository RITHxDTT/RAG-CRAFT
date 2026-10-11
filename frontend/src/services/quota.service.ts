import { apiMode } from '@/config/data-source';
import { apiQuotaService } from './api/quota.api';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { notificationRepository } from '@/repositories/v5.repository';
import { currentAccount } from './access';
import { notify } from './notification.service';
import { ApiError } from './api';
import type { UserAccount } from '@/types/demo';
const NEAR_LIMIT = 0.8;
export interface QuotaUsage { bots: { used: number; limit: number }; storage: { used: number; limit: number } }
export const storageUsed = (userId: string) => knowledgeRepository.all().filter(doc => doc.ownerId === userId).reduce((sum, doc) => sum + doc.size_bytes, 0);
export const botCount = (userId: string) => chatbotRepository.all().filter(bot => bot.ownerId === userId).length;
export function usageFor(user: UserAccount): QuotaUsage {
    return { bots: { used: botCount(user.id), limit: user.quota.max_bots }, storage: { used: storageUsed(user.id), limit: user.quota.max_storage_bytes } };
}
export const overQuota = (user: UserAccount) => { const usage = usageFor(user); return usage.bots.used > usage.bots.limit || usage.storage.used > usage.storage.limit; };
export function assertCanCreateBot(owner: UserAccount) {
    if (botCount(owner.id) >= owner.quota.max_bots)
        throw new ApiError(403, `Chatbot limit reached (${owner.quota.max_bots}). Delete a chatbot or ask an administrator to raise your quota.`);
}
export function assertCanStore(owner: UserAccount, bytes: number) {
    if (storageUsed(owner.id) + bytes > owner.quota.max_storage_bytes)
        throw new ApiError(403, 'Storage limit reached. Delete documents or ask an administrator to raise your quota.');
}
/** Sends at most one near-limit warning per owner per day. */
export function warnIfNearLimit(owner: UserAccount) {
    const usage = usageFor(owner);
    const near = usage.bots.used / usage.bots.limit >= NEAR_LIMIT || usage.storage.used / usage.storage.limit >= NEAR_LIMIT;
    const recent = notificationRepository.all().some(row => row.userId === owner.id && row.type === 'NEAR_LIMIT' && Date.parse(row.created_at) > Date.now() - 86400000);
    if (near && !recent)
        notify(owner.id, 'NEAR_LIMIT', 'You are close to your quota', 'Your chatbots or storage have reached 80% of the allowed limit.', ['PUSH']);
}
export const localQuotaService = { async usage(): Promise<QuotaUsage> { return usageFor(currentAccount()); } };
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const quotaService: typeof localQuotaService = apiMode ? apiQuotaService : localQuotaService;
