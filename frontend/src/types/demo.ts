import type { CurrentUser } from './auth';
export interface UserAccount extends CurrentUser {
  password: string; is_active: boolean; builtIn: boolean; created_at: string; updated_at: string;
}
export interface Session { userId: string; email: string; role: CurrentUser['role']; loggedIn: true }
export interface AnalyticsEvent { id: string; ownerId: string; chatbot_id: string; channel: string; created_at: string }
