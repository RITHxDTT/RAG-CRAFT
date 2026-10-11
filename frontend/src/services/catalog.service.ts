import { apiMode } from '@/config/data-source';
import { apiCatalogService } from './api/catalog.api';
import { repository } from '@/repositories/repository';
import { KEYS } from '@/storage/keys';
import { currentAccount, requireAdmin } from './access';
import { id, delay, required } from '@/utils/demo';
export interface Model {
    id: string;
    name: string;
    provider: 'OLLAMA';
    model_identifier: string;
    /** Chat models answer questions; embedding models index documents. Defaults to LLM. */
    kind?: 'LLM' | 'EMBEDDING';
    enabled: boolean;
    is_default: boolean;
}
export interface Prompt {
    id: string;
    name: string;
    prompt: string;
    enabled: boolean;
}
const models = repository<Model>(KEYS.models), prompts = repository<Prompt>(KEYS.prompts);
function seed() { currentAccount(); if (!models.all().length)
    models.save(['Llama 3.2 3B', 'Qwen', 'Custom Model'].map((name, index) => ({ id: ['llama', 'qwen', 'custom'][index], name, provider: 'OLLAMA', model_identifier: name, kind: 'LLM' as const, enabled: true, is_default: index === 0 })));
    if (!models.all().some(m => m.kind === 'EMBEDDING'))
        ['nomic-embed-text', 'bge-m3'].forEach((name, index) => models.put({ id: `embed_${index}`, name, provider: 'OLLAMA', model_identifier: name, kind: 'EMBEDDING', enabled: true, is_default: index === 0 }));
    if (!prompts.all().length)
    prompts.put({ id: 'default', name: 'Helpful assistant', prompt: 'Answer using the provided knowledge.', enabled: true }); }
export const localCatalogService = {
    async models(admin = false) { seed(); if (admin)
        requireAdmin(); return models.all().filter(m => m.kind !== 'EMBEDDING' && (admin || m.enabled)); },
    async embeddingModels() { seed(); return models.all().filter(m => m.kind === 'EMBEDDING' && m.enabled); },
    async prompts(admin = false) { seed(); if (admin)
        requireAdmin(); return prompts.all().filter(p => admin || p.enabled); },
    async saveModel(data: Omit<Model, 'id'>, uid?: string) { requireAdmin(); await delay(); if (data.is_default)
        models.save(models.all().map(m => (m.kind ?? 'LLM') === (data.kind ?? 'LLM') ? { ...m, is_default: false } : m)); return models.put({ ...data, name: required(data.name, 'Model name'), id: uid || id('model') }); },
    async savePrompt(data: Omit<Prompt, 'id'>, uid?: string) { requireAdmin(); await delay(); return prompts.put({ ...data, name: required(data.name, 'Prompt name'), id: uid || id('prompt') }); },
};
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const catalogService: typeof localCatalogService = apiMode ? apiCatalogService : localCatalogService;
