import { apiMode } from '@/config/data-source';
import { apiAdminService } from './api/admin.api';
import { userRepository } from '@/repositories/auth.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { analyticsRepository } from '@/repositories/conversation.repository';
import { sessionsRepository } from '@/repositories/v5.repository';
import { storage } from '@/storage/local-storage';
import { KEYS } from '@/storage/keys';
import { requireAdmin } from './access';
import { analyticsService } from './analytics.service';
import { deleteAccountData } from './account-data';
import { recordAudit } from './audit.service';
import { closePendingAppeal } from './appeal.service';
import { notify } from './notification.service';
import { overQuota, storageUsed } from './quota.service';
import { validateQuota } from './platform-settings';
import { demoConfig } from '@/config/demo-config';
import { ApiError } from './api';
import { delay, id, now, toast } from '@/utils/demo';
import type { AccountStatus, SignupMethod } from '@/types/auth';
import type { Quota, UserAccount } from '@/types/demo';
export interface UserRecord {
    id: string;
    full_name: string;
    email: string;
    role: string;
    is_active: boolean;
    chatbot_count: number;
    created_at: string;
    builtIn?: boolean;
    status: AccountStatus;
    signup_method: SignupMethod;
    storage_used: number;
    messages_this_month: number;
    last_login_at: string | null;
    quota: Quota;
    over_quota: boolean;
    suspend_reason?: string | null;
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
    disabled_reason?: string | null;
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
export interface UserFilter { status?: AccountStatus; signup_method?: SignupMethod; over_quota?: boolean; inactive_30d?: boolean }
const monthStart = () => { const date = new Date(); date.setDate(1); date.setHours(0, 0, 0, 0); return date.getTime(); };
function record(user: UserAccount): UserRecord {
    const bots = chatbotRepository.all().filter(bot => bot.ownerId === user.id);
    const ids = new Set(bots.map(bot => bot.id));
    return { id: user.id, full_name: user.full_name, email: user.email, role: user.role, is_active: user.status === 'ACTIVE', builtIn: user.builtIn, created_at: user.created_at, chatbot_count: bots.length,
        status: user.status, signup_method: user.signup_method, storage_used: storageUsed(user.id), last_login_at: user.last_login_at ?? null, quota: user.quota, over_quota: overQuota(user), suspend_reason: user.suspend_reason,
        messages_this_month: analyticsRepository.all().filter(event => ids.has(event.chatbot_id) && Date.parse(event.created_at) >= monthStart()).length * 2 };
}
function target(uid: string) {
    const user = userRepository.all().find(u => u.id === uid);
    if (!user) throw new Error('User not found.');
    return user;
}
function mutable(uid: string, admin: UserAccount) {
    const user = target(uid);
    if (user.builtIn) throw new Error('Fixed demo accounts are protected.');
    if (user.id === admin.id) throw new Error('You cannot do this to your own account.');
    return user;
}
const revokeSessions = (uid: string) => sessionsRepository.save(sessionsRepository.all().filter(row => row.userId !== uid));
const userTarget = (user: UserAccount) => ({ target_type: 'USER' as const, target_id: user.id, target_label: user.email });
export const localAdminService = {
    async users(search = '', offset = 0, filter: UserFilter = {}) {
        requireAdmin();
        const cutoff = Date.now() - 30 * 86400000;
        return userRepository.all().map(record).filter(u => `${u.full_name} ${u.email}`.toLowerCase().includes(search.toLowerCase()) && (!filter.status || u.status === filter.status)
            && (!filter.signup_method || u.signup_method === filter.signup_method) && (!filter.over_quota || u.over_quota)
            && (!filter.inactive_30d || Date.parse(u.last_login_at ?? u.created_at) < cutoff)).slice(offset, offset + 100);
    },
    async user(uid: string) { requireAdmin(); return record(target(uid)); },
    /** Suspending signs the user out everywhere and takes their published chatbots offline. */
    async suspend(uid: string, reason: string) {
        const admin = requireAdmin();
        await delay();
        const user = mutable(uid, admin);
        if (user.status === 'SUSPENDED') throw new ApiError(409, 'This account is already suspended.');
        recordAudit(admin, 'SUSPEND_USER', userTarget(user), reason);
        userRepository.put({ ...user, status: 'SUSPENDED', is_active: false, suspend_reason: reason.trim(), updated_at: now() });
        revokeSessions(uid);
        notify(uid, 'ACCOUNT_SUSPENDED', 'Your account was suspended', `${reason.trim()} Reply to this e-mail to appeal.`, ['EMAIL']);
        return record(target(uid));
    },
    async reactivate(uid: string, reason: string) {
        const admin = requireAdmin();
        await delay();
        const user = mutable(uid, admin);
        if (user.status !== 'SUSPENDED') throw new ApiError(409, 'Only suspended accounts can be reactivated.');
        recordAudit(admin, 'REACTIVATE_USER', userTarget(user), reason);
        userRepository.put({ ...user, status: 'ACTIVE', is_active: true, suspend_reason: null, failed_attempts: 0, locked_until: null, updated_at: now() });
        notify(uid, 'ACCOUNT_REACTIVATED', 'Your account was reactivated', 'You can sign in again.', ['EMAIL']);
        return record(target(uid));
    },
    async forceLogout(uid: string, reason: string) {
        const admin = requireAdmin();
        const user = target(uid);
        recordAudit(admin, 'FORCE_LOGOUT', userTarget(user), reason);
        revokeSessions(uid);
        toast('All sessions signed out.');
    },
    /** Simulated reset e-mail; the token is returned so the demo can open the reset page. */
    async sendPasswordReset(uid: string) {
        requireAdmin();
        const user = target(uid);
        const token = id('reset');
        storage.write(KEYS.reset, { token, userId: user.id, expires: Date.now() + demoConfig.linkMinutes * 60000 });
        notify(uid, 'PASSWORD_RESET', 'Reset your password', `Use this demo reset code: ${token}`, ['EMAIL']);
        return { token };
    },
    /** Removes the authenticator app and passkeys so the user can enrol again. */
    async resetMfa(uid: string, reason: string) {
        const admin = requireAdmin();
        const user = target(uid);
        recordAudit(admin, 'RESET_MFA', userTarget(user), reason);
        userRepository.put({ ...user, totp_secret: null, passkeys: [], updated_at: now() });
        revokeSessions(uid);
        return record(target(uid));
    },
    async setQuota(uid: string, quota: Quota, reason: string) {
        const admin = requireAdmin();
        await delay();
        const user = target(uid);
        validateQuota(quota);
        recordAudit(admin, 'SET_QUOTA', userTarget(user), reason, { before: user.quota, after: quota });
        userRepository.put({ ...user, quota, updated_at: now() });
        return record(target(uid));
    },
    /** Disables a chatbot for every guest channel; the owner is told why and may appeal. */
    async disableBot(botId: string, reason: string) {
        const admin = requireAdmin();
        await delay();
        const bot = chatbotRepository.all().find(row => row.id === botId);
        if (!bot) throw new ApiError(404, 'Chatbot not found.');
        if (bot.status === 'DISABLED') throw new ApiError(409, 'This chatbot is already disabled.');
        recordAudit(admin, 'FORCE_DISABLE_CHATBOT', { target_type: 'CHATBOT', target_id: bot.id, target_label: bot.name }, reason, { previous_status: bot.status });
        chatbotRepository.put({ ...bot, status: 'DISABLED', disabled_reason: reason.trim(), disabled_at: now(), updated_at: now() });
        notify(bot.ownerId, 'CHATBOT_DISABLED', 'Your chatbot was disabled', `${bot.name}: ${reason.trim()}`);
        return bot.id;
    },
    /** Lifts a disable without an appeal; the chatbot returns as PAUSED. */
    async reenableBot(botId: string, reason: string) {
        const admin = requireAdmin();
        await delay();
        const bot = chatbotRepository.all().find(row => row.id === botId);
        if (!bot) throw new ApiError(404, 'Chatbot not found.');
        if (bot.status !== 'DISABLED') throw new ApiError(409, 'Only disabled chatbots can be re-enabled.');
        recordAudit(admin, 'REENABLE_CHATBOT', { target_type: 'CHATBOT', target_id: bot.id, target_label: bot.name }, reason);
        chatbotRepository.put({ ...bot, status: 'PAUSED', disabled_reason: null, disabled_at: null, updated_at: now() });
        closePendingAppeal(botId, admin.email);
        return bot.id;
    },
    async remove(uid: string) {
        const admin = requireAdmin();
        const user = userRepository.all().find(u => u.id === uid);
        if (!user || user.builtIn) throw new Error('Fixed demo accounts are protected.');
        if (user.id === admin.id) throw new Error('You cannot delete your own account here.');
        await delay();
        await deleteAccountData(uid);
        toast('Local account and its data deleted.');
    },
    async bots(offset = 0) { requireAdmin(); return chatbotRepository.all().slice(offset, offset + 100).map((b): MonitoredBot => ({ id: b.id, name: b.name, status: b.status, owner_email: userRepository.all().find(u => u.id === b.ownerId)?.email ?? '', owner_name: userRepository.all().find(u => u.id === b.ownerId)?.full_name ?? '', created_at: b.created_at, disabled_reason: b.disabled_reason, knowledge_count: knowledgeRepository.all().filter(d => d.chatbot_id === b.id).length, channel_count: channelRepository.all().filter(c => c.chatbot_id === b.id).length })); },
    usage: analyticsService.usage,
};
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const adminService: typeof localAdminService = apiMode ? apiAdminService : localAdminService;
