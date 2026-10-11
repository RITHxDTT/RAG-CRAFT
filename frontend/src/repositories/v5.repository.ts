import { repository } from './repository';
import { KEYS } from '@/storage/keys';
import { storage } from '@/storage/local-storage';
import type { Appeal, AnswerFeedback, AppNotification, AuditEntry, DeviceSession, PlatformSettings } from '@/types/v5';
export const sessionsRepository = repository<DeviceSession>(KEYS.sessions);
export const appealRepository = repository<Appeal>(KEYS.appeals);
export const notificationRepository = repository<AppNotification>(KEYS.notifications);
export const feedbackRepository = repository<AnswerFeedback>(KEYS.feedback);
/** Public tokens of deleted chatbots, so guests see "no longer available" instead of a generic error. */
export const tombstoneRepository = repository<{ id: string }>(KEYS.tombstones);
const audit = repository<AuditEntry>(KEYS.audit);
/** Append-only: the audit log exposes no update or delete. */
export const auditRepository = {
    all: () => audit.all(),
    append(entry: AuditEntry) { audit.save([...audit.all(), entry]); return entry; },
};
export const platformRepository = {
    get: () => storage.read<PlatformSettings | null>(KEYS.platform, null),
    save: (settings: PlatformSettings) => storage.write(KEYS.platform, settings),
};
export const verificationRepository = repository<{ id: string; userId: string; kind: 'EMAIL' | 'CHANGE_EMAIL'; email: string; expires: number }>(KEYS.verification);
