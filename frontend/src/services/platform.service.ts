import { apiMode } from '@/config/data-source';
import { apiPlatformService } from './api/platform.api';
import { requireAdmin } from './access';
import { delay } from '@/utils/demo';
import { platformSettings, savePlatform, validateQuota, ABSOLUTE, LABEL } from './platform-settings';
import { notifyAdmins } from './notification.service';
import { catalogService } from './catalog.service';
import type { AdvancedLimit, AdvancedSettingKey, PlatformSettings } from '@/types/v5';
export { QUOTA_CEILING } from './platform-settings';
export const localPlatformService = {
    async get() { return platformSettings(); },
    async setLimit(key: AdvancedSettingKey, limit: AdvancedLimit) {
        requireAdmin();
        await delay();
        if (!ABSOLUTE[key]) throw new Error('Unknown setting.');
        const { min, max } = ABSOLUTE[key];
        if (![limit.min, limit.max, limit.default].every(Number.isFinite) || limit.min > limit.default || limit.default > limit.max || limit.min < min || limit.max > max)
            throw new Error(`${LABEL[key]}: use min ≤ default ≤ max within ${min}–${max}.`);
        const settings = platformSettings();
        savePlatform({ ...settings, limits: { ...settings.limits, [key]: limit } });
        return platformSettings();
    },
    async setDefaultQuota(quota: { max_bots: number; max_storage_bytes: number }) {
        requireAdmin();
        await delay();
        validateQuota(quota);
        savePlatform({ ...platformSettings(), default_quota: quota });
        return platformSettings();
    },
    /** Simulated provider health; marking a provider unavailable alerts every admin. */
    async setLlmStatus(provider: string, status: PlatformSettings['llm_status'][string]) {
        requireAdmin();
        const settings = platformSettings();
        savePlatform({ ...settings, llm_status: { ...settings.llm_status, [provider]: status } });
        if (status !== 'OPERATIONAL')
            notifyAdmins('ADMIN_LLM_UNAVAILABLE', 'LLM service unavailable', `${provider} is ${status.toLowerCase()}.`);
        return platformSettings();
    },
    async setSystemFallbackModel(modelName: string | null) {
        requireAdmin();
        if (modelName && !(await catalogService.models(true)).some(model => model.name === modelName && model.enabled))
            throw new Error('Choose an enabled model.');
        savePlatform({ ...platformSettings(), system_fallback_model: modelName });
        return platformSettings();
    },
};
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const platformService: typeof localPlatformService = apiMode ? apiPlatformService : localPlatformService;
