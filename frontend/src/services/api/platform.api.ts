import { get, http } from '../http';
import { ApiError } from '../api';
import type { localPlatformService } from '../platform.service';
import type { AdvancedLimit, AdvancedSettingKey, PlatformSettings } from '@/types/v5';
/* eslint-disable @typescript-eslint/no-explicit-any -- raw server JSON */
type Dto = Record<string, any>;
const unsupported = (feature: string) => async (): Promise<never> => {
    throw new ApiError(501, `${feature} is not available with the database yet.`, 'NOT_IMPLEMENTED');
};
async function load(): Promise<PlatformSettings> {
    const rows = await get<Dto[]>('/api/settings-limits');
    const limits = Object.fromEntries(rows.map(row => [row.key, { min: Number(row.min), max: Number(row.max), default: Number(row.default_value) }])) as Record<AdvancedSettingKey, AdvancedLimit>;
    return { default_quota: { max_bots: 5, max_storage_bytes: 5 * 1024 ** 3 }, limits, system_fallback_model: null, llm_status: { OLLAMA: 'OPERATIONAL' } };
}
/** The admin-defined min, max and default for every advanced chatbot setting live in catalog-service (craftrag_db.catalog). */
export const apiPlatformService: typeof localPlatformService = {
    get: load,
    async setLimit(key, limit) {
        await http('PUT', `/api/admin/settings-limits/${key}`, { body: { min: limit.min, max: limit.max, default_value: limit.default } });
        return load();
    },
    setDefaultQuota: unsupported('Changing the default quota'),
    setLlmStatus: unsupported('Changing LLM status'),
    setSystemFallbackModel: unsupported('Changing the system fallback model'),
};
