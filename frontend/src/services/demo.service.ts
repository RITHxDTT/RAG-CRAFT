import { storage } from '@/storage/local-storage';
import { fileStorage } from '@/storage/indexed-db';
import { seedDemo } from '@/mocks/seed';
import { preferencesService } from './preferences.service';
export const demoService = {
    async reset() {
        // Keep the UI language; everything else in the RAG Craft namespace is cleared.
        const locale = preferencesService.locale();
        await fileStorage.clear();
        storage.clearDemo();
        seedDemo();
        preferencesService.setLocale(locale);
        window.dispatchEvent(new Event('ragcraft:session-expired'));
    },
};
