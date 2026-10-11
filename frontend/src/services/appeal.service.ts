import { apiMode } from '@/config/data-source';
import { apiAppealService } from './api/appeal.api';
import { appealRepository } from '@/repositories/v5.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { userRepository } from '@/repositories/auth.repository';
import { currentAccount, requireAdmin, requireBot } from './access';
import { recordAudit } from './audit.service';
import { notify, notifyAdmins } from './notification.service';
import { ApiError } from './api';
import { delay, id, now, toast } from '@/utils/demo';
import type { Appeal, AppealStatus } from '@/types/v5';
function pending(appealId: string) {
    const appeal = appealRepository.all().find(row => row.id === appealId);
    if (!appeal) throw new ApiError(404, 'Appeal not found.');
    if (appeal.status !== 'PENDING') throw new ApiError(409, 'This appeal has already been decided.');
    return appeal;
}
function decide(appeal: Appeal, status: Exclude<AppealStatus, 'PENDING'>, adminEmail: string, reason: string) {
    const decided: Appeal = { ...appeal, status, decision_reason: reason, decided_by: adminEmail, decided_at: now() };
    appealRepository.put(decided);
    notify(appeal.ownerId, 'APPEAL_DECIDED', status === 'APPROVED' ? 'Your appeal was approved' : 'Your appeal was rejected',
        status === 'APPROVED' ? `${appeal.chatbot_name} is paused again. Resume it when you are ready.` : `${appeal.chatbot_name}: ${reason}`);
    return decided;
}
/** Used by the admin "re-enable" action so a pending appeal never outlives the DISABLED status. */
export function closePendingAppeal(botId: string, adminEmail: string) {
    const open = appealRepository.all().find(row => row.chatbot_id === botId && row.status === 'PENDING');
    if (open) decide(open, 'APPROVED', adminEmail, 'The chatbot was re-enabled by an administrator.');
}
export const localAppealService = {
    /** Owner: one PENDING appeal per DISABLED chatbot, message required (max 500 characters). */
    async submit(botId: string, message: string) {
        await delay();
        const user = currentAccount();
        const bot = requireBot(botId);
        if (bot.ownerId !== user.id)
            throw new ApiError(403, 'Only the chatbot owner can submit an appeal.');
        if (bot.status !== 'DISABLED')
            throw new ApiError(409, 'Only disabled chatbots can be appealed.');
        const text = message.trim();
        if (!text) throw new ApiError(400, 'Describe why the chatbot should be re-enabled.');
        if (text.length > 500) throw new ApiError(400, 'Appeal messages can be up to 500 characters.');
        if (appealRepository.all().some(row => row.chatbot_id === botId && row.status === 'PENDING'))
            throw new ApiError(409, 'You already have a pending appeal for this chatbot.');
        const appeal: Appeal = { id: id('appeal'), chatbot_id: botId, chatbot_name: bot.name, ownerId: user.id, owner_email: user.email, message: text, status: 'PENDING', created_at: now() };
        appealRepository.put(appeal);
        notifyAdmins('ADMIN_NEW_APPEAL', 'New appeal submitted', `${bot.name} (${user.email}) is requesting a review.`);
        toast('Appeal submitted.');
        return appeal;
    },
    /** Owner view of one chatbot's appeals, newest first (rejection reasons included). */
    async forBot(botId: string) { requireBot(botId); return appealRepository.all().filter(row => row.chatbot_id === botId).sort((a, b) => b.created_at.localeCompare(a.created_at)); },
    async list(status?: AppealStatus) {
        requireAdmin();
        return appealRepository.all().filter(row => !status || row.status === status).sort((a, b) => b.created_at.localeCompare(a.created_at));
    },
    async pendingCount() { requireAdmin(); return appealRepository.all().filter(row => row.status === 'PENDING').length; },
    async approve(appealId: string, reason = 'Appeal approved.') {
        const admin = requireAdmin();
        await delay();
        const appeal = pending(appealId);
        const bot = chatbotRepository.all().find(row => row.id === appeal.chatbot_id);
        if (bot?.status === 'DISABLED')
            chatbotRepository.put({ ...bot, status: 'PAUSED', disabled_reason: null, disabled_at: null, updated_at: now() });
        const decided = decide(appeal, 'APPROVED', admin.email, reason);
        recordAudit(admin, 'APPROVE_APPEAL', { target_type: 'APPEAL', target_id: appeal.id, target_label: appeal.chatbot_name }, reason, { chatbot_id: appeal.chatbot_id });
        return decided;
    },
    async reject(appealId: string, reason: string) {
        const admin = requireAdmin();
        if (!reason.trim()) throw new ApiError(400, 'A reason is required to reject an appeal.');
        await delay();
        const appeal = pending(appealId);
        const decided = decide(appeal, 'REJECTED', admin.email, reason.trim());
        recordAudit(admin, 'REJECT_APPEAL', { target_type: 'APPEAL', target_id: appeal.id, target_label: appeal.chatbot_name }, reason, { chatbot_id: appeal.chatbot_id });
        return decided;
    },
    ownerEmail: (ownerId: string) => userRepository.all().find(user => user.id === ownerId)?.email ?? '',
};
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const appealService: typeof localAppealService = apiMode ? apiAppealService : localAppealService;
