import { platformSettings } from '@/services/platform-settings';
import type { BotSettings } from '@/types/chatbot';
import type { UserAccount } from '@/types/demo';
export const defaultSettings: BotSettings = {
    model_id: 'llama', model_name: 'Llama 3.2 3B', fallback_model_name: null, embedding_model: 'nomic-embed-text',
    system_instruction: 'Answer using the provided knowledge.', tone: 'PROFESSIONAL', temperature: 0.4, top_k: 5, answer_length: 'CONCISE',
    language: 'AUTO', formatting: 'RICH', max_tokens: 1024, search_mode: 'HYBRID', max_context_tokens: 4096, chunk_size: 500, chunk_overlap: 50,
    answer_from_documents_only: true, welcome_message: 'Hello! How can I help you today?', show_citations: true,
};
type AccountSeed = Pick<UserAccount, 'id' | 'email' | 'full_name' | 'password' | 'role' | 'organization_id' | 'organization_name'> & Partial<UserAccount>;
/** Builds a complete account record so every code path sees the same shape. */
export function newAccount(seed: AccountSeed): UserAccount {
    const date = new Date().toISOString();
    return { builtIn: false, status: 'ACTIVE', is_active: true, email_verified: true, signup_method: 'EMAIL', failed_attempts: 0, lock_count: 0,
        locked_until: null, last_login_at: null, quota: { ...platformSettings().default_quota }, totp_secret: null, passkeys: [], linked_accounts: [],
        pending_email: null, deletion_requested_at: null, suspend_reason: null, created_at: date, updated_at: date, ...seed };
}
