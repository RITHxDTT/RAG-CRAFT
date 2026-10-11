import { get, patch, post } from '../http';
import { mapModel, mapPrompt } from './mappers';
import type { localCatalogService } from '../catalog.service';
/* eslint-disable @typescript-eslint/no-explicit-any -- raw server JSON */
type Dto = Record<string, any>;
/** Models and prompt templates live in catalog-service (craftrag_db.catalog). Admins see everything; users only enabled items. */
export const apiCatalogService: typeof localCatalogService = {
    async models(admin = false) { return (await get<Dto[]>(admin ? '/api/admin/models' : '/api/models')).map(mapModel); },
    async embeddingModels() { return (await get<Dto[]>('/api/embedding-models')).map(mapModel); },
    async prompts(admin = false) { return (await get<Dto[]>(admin ? '/api/admin/prompt-templates' : '/api/prompt-templates')).map(mapPrompt); },
    async saveModel(data, uid) {
        const body = { name: data.name, provider: data.provider, model_identifier: data.model_identifier, kind: data.kind, enabled: data.enabled, is_default: data.is_default };
        return mapModel(uid ? await patch<Dto>(`/api/admin/models/${uid}`, body) : await post<Dto>('/api/admin/models', body));
    },
    async savePrompt(data, uid) {
        const body = { name: data.name, prompt: data.prompt, enabled: data.enabled };
        return mapPrompt(uid ? await patch<Dto>(`/api/admin/prompt-templates/${uid}`, body) : await post<Dto>('/api/admin/prompt-templates', body));
    },
};
