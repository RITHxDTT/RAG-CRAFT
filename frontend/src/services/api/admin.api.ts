import { del, get, http, post } from '../http';
import { ApiError } from '../api';
import { mapAdminBot, mapAdminUser } from './mappers';
import { analyticsService } from '../analytics.service';
import type { localAdminService } from '../admin.service';
/* eslint-disable @typescript-eslint/no-explicit-any -- raw server JSON */
type Dto = Record<string, any>;
const unsupported = (feature: string) => async (): Promise<never> => {
    throw new ApiError(501, `${feature} is not available with the database yet.`, 'NOT_IMPLEMENTED');
};
/** Account administration (identity-service) and chatbot moderation (chatbot-service). Every action carries a reason that lands in the audit log. */
export const apiAdminService: typeof localAdminService = {
    async users(search = '', offset = 0, filter = {}) {
        const rows = await get<Dto[]>('/api/admin/users', { search, offset, status: filter.status, signup_method: filter.signup_method, over_quota: filter.over_quota ? true : undefined, inactive_30d: filter.inactive_30d ? true : undefined });
        return rows.map(mapAdminUser);
    },
    async user(uid) { return mapAdminUser(await get<Dto>(`/api/admin/users/${uid}`)); },
    async suspend(uid, reason) { return mapAdminUser(await post<Dto>(`/api/admin/users/${uid}/suspend`, { reason })); },
    async reactivate(uid, reason) { return mapAdminUser(await post<Dto>(`/api/admin/users/${uid}/reactivate`, { reason })); },
    async forceLogout(uid, reason) { await post(`/api/admin/users/${uid}/force-logout`, { reason }); },
    sendPasswordReset: unsupported('Sending a password reset from the admin list'),
    resetMfa: unsupported('Resetting MFA'),
    async setQuota(uid, quota, reason) { return mapAdminUser(await http<Dto>('PUT', `/api/admin/users/${uid}/quota`, { body: { max_bots: quota.max_bots, max_storage_bytes: quota.max_storage_bytes, reason } })); },
    async disableBot(botId, reason) { await post(`/api/admin/chatbots/${botId}/disable`, { reason }); return botId; },
    async reenableBot(botId, reason) { await post(`/api/admin/chatbots/${botId}/reenable`, { reason }); return botId; },
    async remove(uid) { await del(`/api/admin/users/${uid}`); },
    async bots(offset = 0) { return (await get<Dto[]>('/api/admin/chatbots', { offset })).map(mapAdminBot); },
    // Platform analytics are not on the database yet; this still reads the browser-local numbers.
    usage: analyticsService.usage,
};
