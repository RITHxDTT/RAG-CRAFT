import { feedbackRepository } from '@/repositories/v5.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { userRepository } from '@/repositories/auth.repository';
import { requireAdmin } from './access';
import { ApiError } from './api';
import type { ReportStatus, ReportedBot } from '@/types/v5';
export interface ModerationFilter { status?: ReportStatus; sort?: 'REPORTS' | 'RECENT' }
export const moderationService = {
    /** Chatbots that have received reports: owner, open report count, last report date. */
    async reportedBots(filter: ModerationFilter = {}): Promise<ReportedBot[]> {
        requireAdmin();
        const reports = feedbackRepository.all().filter(row => row.kind === 'REPORT');
        const rows = chatbotRepository.all().map(bot => {
            const own = reports.filter(row => row.chatbot_id === bot.id);
            const shown = filter.status ? own.filter(row => row.status === filter.status) : own;
            return { bot, own, shown };
        }).filter(({ shown }) => shown.length);
        const list = rows.map(({ bot, own, shown }) => ({ chatbot: { id: bot.id, name: bot.name, status: bot.status }, owner_email: userRepository.all().find(user => user.id === bot.ownerId)?.email ?? '',
            open_reports: own.filter(row => row.status === 'OPEN').length, total_reports: shown.length, last_report_at: shown.map(row => row.created_at).sort().at(-1)! }));
        return list.sort((a, b) => filter.sort === 'RECENT' ? b.last_report_at.localeCompare(a.last_report_at) : b.open_reports - a.open_reports || b.total_reports - a.total_reports);
    },
    /** Reported answers (question, answer, reason, comment) and the documents the chatbot draws from. */
    async review(botId: string) {
        requireAdmin();
        const bot = chatbotRepository.all().find(row => row.id === botId);
        if (!bot) throw new ApiError(404, 'Chatbot not found.');
        return { chatbot: bot, owner: userRepository.all().find(user => user.id === bot.ownerId)?.email ?? '',
            reports: feedbackRepository.all().filter(row => row.chatbot_id === botId && row.kind === 'REPORT').sort((a, b) => b.created_at.localeCompare(a.created_at)),
            documents: knowledgeRepository.all().filter(doc => doc.chatbot_id === botId).map(doc => ({ id: doc.id, name: doc.name, file_type: doc.file_type, status: doc.status })) };
    },
    async openReportCount() { requireAdmin(); return feedbackRepository.all().filter(row => row.kind === 'REPORT' && row.status === 'OPEN').length; },
};
