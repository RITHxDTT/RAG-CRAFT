import { seedDemo } from '@/mocks/seed';
import { userRepository, sessionRepository } from '@/repositories/auth.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { sessionsRepository } from '@/repositories/v5.repository';
import { demoConfig } from '@/config/demo-config';
import { apiMode } from '@/config/data-source';
import { apiToken } from './http';
import { ApiError } from './api';
import { deletionDeadline, purgeExpiredAccounts } from './account-data';
import type { UserAccount } from '@/types/demo';
function expire(status: number, message: string): never {
    sessionRepository.remove();
    window.dispatchEvent(new Event('ragcraft:session-expired'));
    throw new ApiError(status, message);
}
export function currentAccount(options: { allowPendingDeletion?: boolean } = {}): UserAccount {
    seedDemo();
    const session = sessionRepository.get();
    const user = userRepository.all().find(user => user.id === session?.userId);
    if (!session?.loggedIn || !user)
        return expire(401, 'Please sign in to continue.');
    if (apiMode) {
        // The server owns the session: an expired or revoked token ends it. Account status is enforced by the services themselves.
        if (!apiToken.get()) return expire(401, 'Your session has expired. Sign in again.');
        if (user.status === 'PENDING_DELETION' && !options.allowPendingDeletion)
            throw new ApiError(403, `This account is scheduled for deletion. Restore it within ${demoConfig.deletionGraceDays} days to continue.`);
        return user;
    }
    if (session.expires_at && Date.parse(session.expires_at) < Date.now())
        return expire(401, 'Your session has expired. Sign in again.');
    // A session revoked from another "device" (or by an admin) no longer has a row.
    if (session.sessionId && !sessionsRepository.all().some(row => row.id === session.sessionId))
        return expire(401, 'This session was signed out. Sign in again.');
    if (user.status === 'PENDING_DELETION') {
        if (user.deletion_requested_at && Date.parse(deletionDeadline(user.deletion_requested_at)) <= Date.now()) {
            void purgeExpiredAccounts();
            return expire(401, 'This account has been permanently deleted.');
        }
        if (options.allowPendingDeletion)
            return user;
        throw new ApiError(403, `This account is scheduled for deletion. Restore it within ${demoConfig.deletionGraceDays} days to continue.`);
    }
    if (user.status === 'SUSPENDED')
        return expire(403, 'This account is suspended.');
    if (user.status !== 'ACTIVE')
        return expire(403, 'Verify your email to continue.');
    return user;
}
export function requireAdmin() { const user = currentAccount(); if (user.role !== 'ADMIN')
    throw new ApiError(403, 'Administrator access required.'); return user; }
export function accessibleBots() { const user = currentAccount(); return chatbotRepository.all().filter(bot => user.role === 'ADMIN' || bot.ownerId === user.id); }
export function requireBot(id: string) { const bot = accessibleBots().find(bot => bot.id === id); if (!bot)
    throw new ApiError(404, 'Chatbot not found.'); return bot; }
