import { apiMode } from '@/config/data-source';
import { apiAuditService } from './api/audit.api';
import { auditRepository } from '@/repositories/v5.repository';
import { requireAdmin } from './access';
import { id, now } from '@/utils/demo';
import type { AuditAction, AuditEntry } from '@/types/v5';
import type { UserAccount } from '@/types/demo';
/** Every admin action calls this; a reason is mandatory. */
export function recordAudit(admin: UserAccount, action: AuditAction, target: Pick<AuditEntry, 'target_type' | 'target_id' | 'target_label'>, reason: string, details: Record<string, unknown> = {}) {
    if (!reason.trim())
        throw new Error('A reason is required.');
    return auditRepository.append({ id: id('audit'), admin_id: admin.id, admin_email: admin.email, action, ...target, reason: reason.trim(), details, created_at: now() });
}
export interface AuditFilter { admin?: string; action?: AuditAction; target?: string; from?: string; to?: string }
export const localAuditService = {
    async list(filter: AuditFilter = {}) {
        requireAdmin();
        const from = filter.from ? Date.parse(filter.from) : -Infinity, to = filter.to ? Date.parse(filter.to) + 86400000 : Infinity;
        return auditRepository.all().filter(row => (!filter.admin || row.admin_email.toLowerCase().includes(filter.admin.toLowerCase())) && (!filter.action || row.action === filter.action)
            && (!filter.target || `${row.target_type} ${row.target_id} ${row.target_label}`.toLowerCase().includes(filter.target.toLowerCase()))
            && Date.parse(row.created_at) >= from && Date.parse(row.created_at) < to).sort((a, b) => b.created_at.localeCompare(a.created_at));
    },
    async get(entryId: string) {
        requireAdmin();
        const row = auditRepository.all().find(entry => entry.id === entryId);
        if (!row) throw new Error('Audit entry not found.');
        return row;
    },
};
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const auditService: typeof localAuditService = apiMode ? apiAuditService : localAuditService;
