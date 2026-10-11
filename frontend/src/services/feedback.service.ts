import { feedbackRepository } from '@/repositories/v5.repository';
import { requireBot } from './access';
import { resolveChannel } from './public-resolve';
import { notify, notifyAdmins } from './notification.service';
import { ApiError } from './api';
import { id, now } from '@/utils/demo';
import type { Chatbot } from '@/types/chatbot';
import type { AnswerFeedback, ReportReason, ReportStatus } from '@/types/v5';
interface GuestAnswer { botId: string; channel: string; question: string; answer: string }
// Guest conversations are not stored. The page session keeps the last answers only so feedback can refer to them.
const guestAnswers = new Map<string, GuestAnswer>();
export function rememberGuestAnswer(messageId: string, value: GuestAnswer) {
    guestAnswers.set(messageId, value);
    if (guestAnswers.size > 200)
        guestAnswers.delete(guestAnswers.keys().next().value as string);
}
/** One feedback per answer, whether it is a rating or a report. Only reports keep the question and answer. */
function record(bot: Chatbot, channel: string, messageId: string, input: Pick<AnswerFeedback, 'kind' | 'rating' | 'reason' | 'comment'>) {
    const answer = guestAnswers.get(messageId);
    if (!answer || answer.botId !== bot.id)
        throw new ApiError(404, 'This answer is no longer available for feedback.');
    if (feedbackRepository.all().some(row => row.message_id === messageId))
        throw new ApiError(409, 'You already sent feedback for this answer.');
    const isReport = input.kind === 'REPORT';
    if (isReport && input.reason !== 'INACCURATE' && input.reason !== 'UNHELPFUL')
        throw new ApiError(400, 'Choose a reason: inaccurate or unhelpful.');
    if (input.comment && input.comment.length > 500)
        throw new ApiError(400, 'Comments can be up to 500 characters.');
    if (!isReport && input.rating !== 'HELPFUL' && input.rating !== 'NOT_HELPFUL')
        throw new ApiError(400, 'Choose helpful or not helpful.');
    const row: AnswerFeedback = { id: id('feedback'), ownerId: bot.ownerId, chatbot_id: bot.id, channel, message_id: messageId, kind: input.kind, created_at: now(),
        ...(isReport ? { reason: input.reason, comment: input.comment?.trim() || undefined, question: answer.question, answer: answer.answer, status: 'OPEN' as ReportStatus } : { rating: input.rating }) };
    feedbackRepository.put(row);
    if (isReport) {
        const inaccurate = input.reason === 'INACCURATE';
        notify(bot.ownerId, inaccurate ? 'REPORT_INACCURATE' : 'REPORT_UNHELPFUL', inaccurate ? 'Inaccurate response reported' : 'Unhelpful response reported', `A guest reported an answer from ${bot.name}.`, ['PUSH']);
        notifyAdmins('ADMIN_USER_REPORT', 'New reported answer', `${bot.name} received a report.`);
    }
    return row;
}
export interface ReportFilter { status?: ReportStatus; reason?: ReportReason }
export const feedbackService = {
    /** Guest actions, available on the public link and the web widget (and Telegram once it has a guest flow). */
    async rate(kind: string, token: string, messageId: string, rating: 'HELPFUL' | 'NOT_HELPFUL') {
        const { bot, channel } = resolveChannel(kind, token);
        return record(bot, channel.channel, messageId, { kind: 'RATING', rating });
    },
    async report(kind: string, token: string, messageId: string, reason: ReportReason, comment?: string) {
        const { bot, channel } = resolveChannel(kind, token);
        return record(bot, channel.channel, messageId, { kind: 'REPORT', reason, comment });
    },
    /** Owner view of one chatbot's reported answers. */
    async reports(botId: string, filter: ReportFilter = {}) {
        requireBot(botId);
        return feedbackRepository.all().filter(row => row.chatbot_id === botId && row.kind === 'REPORT' && (!filter.status || row.status === filter.status) && (!filter.reason || row.reason === filter.reason))
            .sort((a, b) => b.created_at.localeCompare(a.created_at));
    },
    async setStatus(botId: string, feedbackId: string, status: Exclude<ReportStatus, 'OPEN'>) {
        requireBot(botId);
        const row = feedbackRepository.all().find(item => item.id === feedbackId && item.chatbot_id === botId && item.kind === 'REPORT');
        if (!row)
            throw new ApiError(404, 'Report not found.');
        if (row.status !== 'OPEN')
            throw new ApiError(409, 'This report has already been handled.');
        feedbackRepository.put({ ...row, status });
        return { ...row, status };
    },
};
