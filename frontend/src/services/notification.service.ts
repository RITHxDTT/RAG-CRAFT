import { notificationRepository } from '@/repositories/v5.repository';
import { userRepository } from '@/repositories/auth.repository';
import { currentAccount } from './access';
import { id, now } from '@/utils/demo';
import type { AppNotification, NotificationType } from '@/types/v5';
type Channel = AppNotification['channel'];
/** Records a simulated e-mail and/or push message for a user. Nothing leaves the browser. */
export function notify(userId: string, type: NotificationType, title: string, body: string, channels: Channel[] = ['EMAIL', 'PUSH']) {
    for (const channel of channels)
        notificationRepository.put({ id: id('note'), userId, channel, type, title, body, read: false, created_at: now() });
}
export function notifyAdmins(type: NotificationType, title: string, body: string, channels: Channel[] = ['PUSH']) {
    userRepository.all().filter(user => user.role === 'ADMIN' && user.status === 'ACTIVE').forEach(admin => notify(admin.id, type, title, body, channels));
}
export const notificationService = {
    async list(channel?: Channel) {
        const user = currentAccount();
        return notificationRepository.all().filter(row => row.userId === user.id && (!channel || row.channel === channel)).sort((a, b) => b.created_at.localeCompare(a.created_at));
    },
    async unreadCount() { return (await this.list('PUSH')).filter(row => !row.read).length; },
    async markRead(noteId?: string) {
        const user = currentAccount();
        notificationRepository.save(notificationRepository.all().map(row => row.userId === user.id && (!noteId || row.id === noteId) ? { ...row, read: true } : row));
    },
};
