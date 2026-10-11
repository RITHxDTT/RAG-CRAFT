import { get } from '../http';
import type { localAuditService } from '../audit.service';
import type { AuditEntry } from '@/types/v5';
/* eslint-disable @typescript-eslint/no-explicit-any -- raw server JSON */
type Dto = Record<string, any>;
const mapEntry = (dto: Dto): AuditEntry => ({ id: dto.id, admin_id: dto.admin_id ?? '', admin_email: dto.admin_email, action: dto.action, target_type: dto.target_type, target_id: dto.target_id,
    target_label: dto.target_label, reason: dto.reason, details: dto.details ?? {}, created_at: dto.created_at });
/** The audit log lives in identity-service (craftrag_db.identity.audit_logs) and cannot be edited or deleted. */
export const apiAuditService: typeof localAuditService = {
    async list(filter = {}) { return (await get<Dto[]>('/api/admin/audit-logs', { admin: filter.admin, action: filter.action, target: filter.target, from: filter.from, to: filter.to })).map(mapEntry); },
    async get(entryId) { return mapEntry(await get<Dto>(`/api/admin/audit-logs/${entryId}`)); },
};
