import type { AccountStatus, CurrentUser, SignupMethod, SocialProvider } from './auth';
export interface Quota { max_bots: number; max_storage_bytes: number }
export interface Passkey { id: string; name: string; created_at: string }
export interface UserAccount extends CurrentUser {
  password: string;
  /** Mirrors `status === "ACTIVE"`; kept for existing screens. */
  is_active: boolean;
  builtIn: boolean;
  created_at: string;
  updated_at: string;
  status: AccountStatus;
  signup_method: SignupMethod;
  email_verified: boolean;
  suspend_reason?: string | null;
  failed_attempts: number;
  lock_count: number;
  locked_until?: string | null;
  last_login_at?: string | null;
  quota: Quota;
  totp_secret?: string | null;
  passkeys: Passkey[];
  linked_accounts: SocialProvider[];
  pending_email?: string | null;
  deletion_requested_at?: string | null;
}
export interface Session { userId: string; email: string; role: CurrentUser['role']; loggedIn: true; sessionId?: string; expires_at?: string }
export interface AnalyticsEvent {
  id: string; ownerId: string; chatbot_id: string; channel: string; created_at: string;
  /** Guest session or playground conversation; used for session and guest counts. */
  session_id?: string; guest_id?: string; question?: string; unanswered?: boolean;
  /** Estimated from text length; the demo performs no real model call. */
  tokens?: number; model?: string;
}
