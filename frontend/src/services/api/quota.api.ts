import { get } from '../http';
import type { QuotaUsage } from '../quota.service';
export const apiQuotaService = {
    async usage(): Promise<QuotaUsage> {
        const quota = await get<QuotaUsage>('/api/quota');
        return { bots: quota.bots, storage: quota.storage };
    },
};
