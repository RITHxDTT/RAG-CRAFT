// One-time copy of the earlier demo namespace into v5. The v2 keys are left untouched.
import { KEYS, LEGACY_PREFIX } from './keys';
import { newAccount, defaultSettings } from '@/mocks/defaults';
import type { Chatbot, ChatbotStatus, BotSettings, AnswerLength, Tone } from '@/types/chatbot';
import type { UserAccount } from '@/types/demo';
type Raw = Record<string, unknown>;
const read = <T,>(name: string): T | null => {
    try { const value = window.localStorage.getItem(`${LEGACY_PREFIX}${name}`); return value === null ? null : JSON.parse(value) as T; }
    catch { return null; }
};
const write = (key: string, value: unknown) => window.localStorage.setItem(key, JSON.stringify(value));
const STATUS: Record<string, ChatbotStatus> = { ACTIVE: 'ACTIVE', DRAFT: 'DRAFT', INACTIVE: 'PAUSED', ERROR: 'PAUSED' };
const TONE: Record<string, Tone> = { PROFESSIONAL: 'PROFESSIONAL', FRIENDLY: 'FRIENDLY', CONCISE: 'PROFESSIONAL', EDUCATIONAL: 'FRIENDLY', DETAILED: 'PROFESSIONAL' };
export function migrateSettings(old: Partial<BotSettings> & Raw): BotSettings {
    const legacyLength = String(old.answer_length ?? '');
    const length: AnswerLength = legacyLength === 'LONG' || legacyLength === 'DETAILED' || String(old.tone) === 'DETAILED' ? 'DETAILED' : 'CONCISE';
    return { ...defaultSettings, ...old, tone: TONE[String(old.tone)] ?? 'PROFESSIONAL', answer_length: length,
        temperature: typeof old.temperature === 'number' ? Math.min(1, Math.max(0, old.temperature)) : defaultSettings.temperature,
        top_k: typeof old.top_k === 'number' ? Math.min(20, Math.max(1, old.top_k)) : defaultSettings.top_k };
}
export function migrateFromV2() {
    if (typeof window === 'undefined' || window.localStorage.getItem(KEYS.migrated)) return;
    window.localStorage.setItem(KEYS.migrated, new Date().toISOString());
    const users = read<Raw[]>('users');
    if (!users) return;
    write(KEYS.users, users.map(user => newAccount({ ...(user as unknown as UserAccount), status: user.is_active === false ? 'SUSPENDED' : 'ACTIVE', is_active: user.is_active !== false,
        suspend_reason: user.is_active === false ? 'Disabled before the v5 upgrade.' : null })));
    const documents = read<{ chatbot_id: string; status: string }[]>('knowledge') ?? [];
    write(KEYS.chatbots, (read<Raw[]>('chatbots') ?? []).map(bot => {
        const mapped = STATUS[String(bot.status)] ?? 'DRAFT';
        const hasReady = documents.some(doc => doc.chatbot_id === bot.id && doc.status === 'READY');
        return { ...bot, status: mapped === 'DRAFT' && hasReady ? 'PENDING' : mapped, settings: migrateSettings((bot.settings ?? {}) as Raw) } as unknown as Chatbot;
    }));
    for (const [name, key] of [['knowledge', KEYS.knowledge], ['channels', KEYS.channels], ['conversations', KEYS.conversations], ['analytics', KEYS.analytics],
        ['preferences', KEYS.preferences], ['models', KEYS.models], ['prompts', KEYS.prompts]] as const) {
        const rows = read<unknown>(name);
        if (rows !== null) write(key, rows);
    }
    if (read<boolean>('seeded')) write(KEYS.seeded, true);
}
