import { del, download, get, http, post } from '../http';
import { ApiError } from '../api';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { mapDoc, mapDocDetail, mirrorDocs } from './mappers';
import type { localKnowledgeService } from '../knowledge.service';
import type { KnowledgeDocument } from '@/types/document';
/* eslint-disable @typescript-eslint/no-explicit-any -- raw server JSON */
type Dto = Record<string, any>;
const unsupported = (feature: string) => async (): Promise<never> => {
    throw new ApiError(501, `${feature} is not available with the database yet.`, 'NOT_IMPLEMENTED');
};
async function fetchList(botId: string): Promise<KnowledgeDocument[]> {
    const docs = (await get<Dto[]>(`/api/chatbots/${botId}/documents`)).map(mapDoc);
    mirrorDocs(botId, docs);
    return docs;
}
function formWith(file: File) { const form = new FormData(); form.append('file', file); return form; }
/** Documents live in knowledge-service (craftrag_db.knowledge). The simulated ingestion pipeline runs on the server. */
export const apiKnowledgeService: typeof localKnowledgeService = {
    async availability(bot, doc) { return get<{ available: boolean }>(`/api/chatbots/${bot}/documents/${doc}/availability`); },
    async policy() { return get<Dto>('/api/knowledge/config').then(dto => ({ max_upload_size_mb: dto.max_upload_size_mb, extensions: dto.extensions, processing_enabled: dto.processing_enabled, worker_running: dto.worker_running })); },
    async recent() {
        const rows = await get<Dto[]>('/api/knowledge/recent');
        return rows.map(row => ({ id: row.id, ownerId: row.owner_id, chatbot_id: row.chatbot_id, chatbot_name: row.chatbot_name, name: row.name, file_type: row.file_type, mime_type: '', size_bytes: 0,
            status: row.status, chunkCount: row.chunk_count ?? 0, error_message: null, created_at: row.created_at, updated_at: row.updated_at, jobs: [] }));
    },
    async list(bot, filter = {}) {
        const search = filter.search?.trim().toLowerCase();
        return (await fetchList(bot)).filter(doc => (!search || doc.name.toLowerCase().includes(search)) && (!filter.type || doc.file_type === filter.type.toUpperCase()) && (!filter.status || doc.status === filter.status))
            .map(doc => ({ ...doc, jobs: [] }));
    },
    async detail(bot, doc) { return mapDocDetail(await get<Dto>(`/api/chatbots/${bot}/documents/${doc}`)); },
    /** The original file is fetched with the session token, then opened from a temporary local URL. */
    async openFile(bot, doc, page = 1) {
        const row = knowledgeRepository.all().find(item => item.id === doc);
        if (row?.url) {
            window.open(row.url, '_blank', 'noopener,noreferrer');
            return;
        }
        const url = URL.createObjectURL(await download(`/api/chatbots/${bot}/documents/${doc}/download`));
        window.open(`${url}#page=${page}`, '_blank', 'noopener,noreferrer');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    },
    /** Same file name in the same chatbot is a duplicate; the caller chooses Replace or Skip. */
    duplicateOf(botId, name) { return knowledgeRepository.all().find(row => row.chatbot_id === botId && row.name.trim().toLowerCase() === name.trim().toLowerCase()); },
    async upload(botId, file, onDuplicate) {
        const duplicate = this.duplicateOf(botId, file.name);
        if (duplicate && onDuplicate === 'SKIP') return duplicate as never;
        if (duplicate && onDuplicate === 'REPLACE') return this.replace(botId, duplicate.id, file);
        // Without a choice the server answers 409 DUPLICATE_NAME for a name that already exists.
        const row = mapDocDetail(await http<Dto>('POST', `/api/chatbots/${botId}/documents`, { form: formWith(file) }));
        await fetchList(botId);
        return row;
    },
    async website(botId, name, value) {
        const row = mapDocDetail(await post<Dto>(`/api/chatbots/${botId}/documents/website`, { name, url: value }));
        await fetchList(botId);
        return row;
    },
    crawlPreview: unsupported('Site crawling'),
    importCrawl: unsupported('Site crawling'),
    rename: unsupported('Renaming documents'),
    async replace(bot, doc, file) {
        const row = mapDocDetail(await http<Dto>('PUT', `/api/chatbots/${bot}/documents/${doc}/replace`, { form: formWith(file) }));
        await fetchList(bot);
        return row;
    },
    async delete(bot, doc) { await del(`/api/chatbots/${bot}/documents/${doc}`); await fetchList(bot); },
    async retry(bot, doc) { const row = mapDocDetail(await post<Dto>(`/api/chatbots/${bot}/documents/${doc}/retry`)); await fetchList(bot); return row; },
    async reindex(bot, doc) { const row = mapDocDetail(await post<Dto>(`/api/chatbots/${bot}/documents/${doc}/reindex`)); await fetchList(bot); return row; },
};
