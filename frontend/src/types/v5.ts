import type { Chatbot } from './chatbot';
export type AppealStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export interface Appeal {
  id: string; chatbot_id: string; chatbot_name: string; ownerId: string; owner_email: string;
  message: string; status: AppealStatus; decision_reason?: string | null; decided_by?: string | null;
  created_at: string; decided_at?: string | null;
}
export type AuditAction = 'FORCE_DISABLE_CHATBOT' | 'REENABLE_CHATBOT' | 'SUSPEND_USER' | 'REACTIVATE_USER' | 'APPROVE_APPEAL'
  | 'REJECT_APPEAL' | 'SET_QUOTA' | 'RESET_MFA' | 'FORCE_LOGOUT';
export interface AuditEntry {
  id: string; admin_id: string; admin_email: string; action: AuditAction;
  target_type: 'USER' | 'CHATBOT' | 'APPEAL'; target_id: string; target_label: string;
  reason: string; details: Record<string, unknown>; created_at: string;
}
export type NotificationType = 'KB_PROCESSING_FAILED' | 'CRAWL_FAILED' | 'LLM_FAILED' | 'NEAR_LIMIT' | 'REPORT_INACCURATE'
  | 'REPORT_UNHELPFUL' | 'CHATBOT_DISABLED' | 'APPEAL_DECIDED' | 'ACCOUNT_SUSPENDED' | 'ACCOUNT_REACTIVATED' | 'PASSWORD_RESET'
  | 'EMAIL_VERIFICATION' | 'ADMIN_USER_REPORT' | 'ADMIN_NEW_APPEAL' | 'ADMIN_LLM_UNAVAILABLE';
/** Simulated e-mail / push message; nothing is sent outside the browser. */
export interface AppNotification {
  id: string; userId: string; channel: 'EMAIL' | 'PUSH'; type: NotificationType;
  title: string; body: string; read: boolean; created_at: string;
}
export type ReportReason = 'INACCURATE' | 'UNHELPFUL';
export type ReportStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED';
/** One feedback per answer: either a rating or a report. Only reports keep the question and answer. */
export interface AnswerFeedback {
  id: string; ownerId: string; chatbot_id: string; channel: string; message_id: string;
  kind: 'RATING' | 'REPORT'; rating?: 'HELPFUL' | 'NOT_HELPFUL';
  reason?: ReportReason; comment?: string; question?: string; answer?: string; status?: ReportStatus; created_at: string;
}
export interface DeviceSession { id: string; userId: string; device: string; location: string; created_at: string; last_active_at: string }
export interface AdvancedLimit { min: number; max: number; default: number }
export type AdvancedSettingKey = 'temperature' | 'max_tokens' | 'top_k' | 'max_context_tokens' | 'chunk_size' | 'chunk_overlap';
export interface PlatformSettings {
  default_quota: { max_bots: number; max_storage_bytes: number };
  limits: Record<AdvancedSettingKey, AdvancedLimit>;
  system_fallback_model: string | null;
  llm_status: Record<string, 'OPERATIONAL' | 'DEGRADED' | 'DOWN'>;
}
export interface ReportedBot { chatbot: Pick<Chatbot, 'id' | 'name' | 'status'>; owner_email: string; open_reports: number; total_reports: number; last_report_at: string }
