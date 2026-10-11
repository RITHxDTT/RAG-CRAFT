import { platformRepository } from '@/repositories/v5.repository';
import type { BotSettings } from '@/types/chatbot';
import type { AdvancedSettingKey, PlatformSettings } from '@/types/v5';
const GB = 1024 ** 3;
/** Hard ceilings an admin can lower a user's quota to, never above. */
export const QUOTA_CEILING = { max_bots: 5, max_storage_bytes: 5 * GB };
export const ABSOLUTE: Record<AdvancedSettingKey, { min: number; max: number }> = {
    temperature: { min: 0, max: 1 }, top_k: { min: 1, max: 20 }, max_tokens: { min: 1, max: 32768 },
    max_context_tokens: { min: 1, max: 131072 }, chunk_size: { min: 50, max: 8000 }, chunk_overlap: { min: 0, max: 4000 },
};
export const LABEL: Record<AdvancedSettingKey, string> = { temperature: 'Temperature', top_k: 'Top K results', max_tokens: 'Max response length',
    max_context_tokens: 'Max context size', chunk_size: 'Chunk size', chunk_overlap: 'Chunk overlap' };
const DEFAULTS: PlatformSettings = {
    default_quota: { ...QUOTA_CEILING },
    limits: {
        temperature: { min: 0, max: 1, default: 0.4 }, max_tokens: { min: 128, max: 4096, default: 1024 }, top_k: { min: 1, max: 20, default: 5 },
        max_context_tokens: { min: 512, max: 16384, default: 4096 }, chunk_size: { min: 100, max: 2000, default: 500 }, chunk_overlap: { min: 0, max: 500, default: 50 },
    },
    system_fallback_model: null, llm_status: { OLLAMA: 'OPERATIONAL' },
};
export function platformSettings(): PlatformSettings {
    const stored = platformRepository.get();
    return stored ? { ...DEFAULTS, ...stored, limits: { ...DEFAULTS.limits, ...stored.limits } } : DEFAULTS;
}
export const advancedKeys = Object.keys(ABSOLUTE) as AdvancedSettingKey[];
/** Fills defaults from the admin policy and rejects values outside the admin-defined range. */
export function applyAdvancedSettings(input: Partial<BotSettings>, base?: Partial<BotSettings>): Pick<BotSettings, AdvancedSettingKey> {
    const { limits } = platformSettings();
    const out = {} as Record<AdvancedSettingKey, number>;
    for (const key of advancedKeys) {
        // Existing values are clamped so tightening a platform limit never blocks unrelated edits.
        const inherited = base?.[key] === undefined ? undefined : Math.min(limits[key].max, Math.max(limits[key].min, base[key] as number));
        const value = input[key] ?? inherited ?? limits[key].default;
        if (!Number.isFinite(value) || value < limits[key].min || value > limits[key].max)
            throw new Error(`${LABEL[key]} must be between ${limits[key].min} and ${limits[key].max}.`);
        out[key] = value;
    }
    if (out.chunk_overlap >= out.chunk_size)
        throw new Error('Chunk overlap must be smaller than the chunk size.');
    return out;
}
export function validateQuota(quota: { max_bots: number; max_storage_bytes: number }) {
    if (!Number.isInteger(quota.max_bots) || quota.max_bots < 1 || quota.max_bots > QUOTA_CEILING.max_bots)
        throw new Error(`Bots must be a whole number from 1 to ${QUOTA_CEILING.max_bots}.`);
    if (!Number.isFinite(quota.max_storage_bytes) || quota.max_storage_bytes < 1 || quota.max_storage_bytes > QUOTA_CEILING.max_storage_bytes)
        throw new Error('Storage must be between 1 byte and 5 GB.');
}
export function savePlatform(settings: PlatformSettings) { platformRepository.save(settings); }
