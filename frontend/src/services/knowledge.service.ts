import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { fileStorage } from '@/storage/indexed-db';
import { requireBot, accessibleBots } from './access';
import { delay, id, now, required, toast } from '@/utils/demo';
import type { DocumentDetail, KnowledgeDocument } from '@/types/document';
const extensions = ['pdf', 'docx', 'txt', 'md', 'xlsx'];
function document(bot: string, doc: string) {
    requireBot(bot);
    const row = knowledgeRepository.all().find(row => row.chatbot_id === bot && row.id === doc);
    if (!row)
        throw new Error('Knowledge source not found.');
    return row;
}
// Derive stages from persisted timestamps so processing also resumes after a reload.
function progress(row: DocumentDetail): DocumentDetail {
    if (!row.processingStartedAt || ['READY', 'FAILED', 'REVIEW'].includes(row.status))
        return row;
    const stages: KnowledgeDocument['status'][] = row.url ? ['QUEUED', 'CRAWLING', 'EXTRACTING', 'CHUNKING', 'INDEXING', 'READY'] : ['QUEUED', 'UPLOADING', 'PROCESSING', 'CHUNKING', 'INDEXING', 'READY'];
    const step = Math.min(5, Math.floor((Date.now() - Date.parse(row.processingStartedAt)) / 900));
    const status = stages[step];
    if (row.status === status)
        return row;
    const date = now();
    const updated = { ...row, status, updated_at: date, chunkCount: status === 'READY' ? Math.max(1, Math.ceil(row.size_bytes / 6000)) : 0,
        jobs: [...row.jobs, { id: id('job'), status, created_at: date, updated_at: date, error_message: null }] };
    knowledgeRepository.put(updated);
    if (status === 'READY')
        toast(`${row.name}: simulated processing complete.`);
    return updated;
}
function queue(row: DocumentDetail) {
    const date = now();
    const updated: DocumentDetail = { ...row, status: 'QUEUED', chunkCount: 0, error_message: null, processingStartedAt: date, updated_at: date,
        jobs: [{ id: id('job'), status: 'QUEUED', error_message: null, created_at: date, updated_at: date }] };
    knowledgeRepository.put(updated);
    return updated;
}
export const knowledgeService = {
    async availability(bot: string, doc: string) { document(bot, doc); return { available: !!(await fileStorage.get(doc)) }; },
    async policy() { return { max_upload_size_mb: 20, extensions, processing_enabled: true, worker_running: true }; },
    async recent() { const bots = accessibleBots(); return knowledgeRepository.all().filter(row => bots.some(bot => bot.id === row.chatbot_id)).map(progress).sort((a, b) => b.updated_at.localeCompare(a.updated_at)).slice(0, 10).map(row => ({ ...row, chatbot_name: bots.find(bot => bot.id === row.chatbot_id)!.name })); },
    async list(bot: string) { requireBot(bot); return knowledgeRepository.all().filter(row => row.chatbot_id === bot).map(progress); },
    async detail(bot: string, doc: string) { return progress(document(bot, doc)); },
    async openFile(bot: string, doc: string, page = 1) {
        const row = document(bot, doc);
        if (row.url) {
            window.open(row.url, '_blank', 'noopener,noreferrer');
            return;
        }
        const blob = await fileStorage.get(doc);
        if (!blob)
            throw new Error('Seed sources contain demo metadata only. Upload a file to preview its original content.');
        const url = URL.createObjectURL(blob);
        window.open(`${url}#page=${page}`, '_blank', 'noopener,noreferrer');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    },
    async upload(botId: string, file: File) {
        const bot = requireBot(botId);
        const ext = file.name.split('.').pop()?.toLowerCase();
        if (!ext || !extensions.includes(ext))
            throw new Error('Unsupported file type.');
        if (!file.size || file.size > 20 * 1024 * 1024)
            throw new Error('Choose a nonempty file up to 20 MB.');
        const docId = id('doc');
        await fileStorage.put(docId, file);
        await delay();
        const date = now();
        const row = queue({ id: docId, ownerId: bot.ownerId, chatbot_id: botId, name: file.name, file_type: ext.toUpperCase(), mime_type: file.type, size_bytes: file.size, status: 'QUEUED', chunkCount: 0, error_message: null, created_at: date, updated_at: date, jobs: [] });
        toast('Document uploaded.');
        return row;
    },
    async website(botId: string, name: string, value: string) {
        const bot = requireBot(botId);
        const url = new URL(value);
        if (!['https:', 'http:'].includes(url.protocol))
            throw new Error('Use an HTTP or HTTPS website URL.');
        await delay();
        const date = now();
        return queue({ id: id('doc'), ownerId: bot.ownerId, chatbot_id: botId, name: required(name, 'Source name'), url: url.href, file_type: 'WEBSITE', mime_type: 'text/html', size_bytes: 48000, status: 'QUEUED', chunkCount: 0, error_message: null, created_at: date, updated_at: date, jobs: [] });
    },
    async replace(bot: string, doc: string, file: File) {
        const row = document(bot, doc);
        const ext = file.name.split('.').pop()?.toLowerCase();
        if (!ext || !extensions.includes(ext) || !file.size || file.size > 20 * 1024 * 1024)
            throw new Error('Choose a supported, nonempty file up to 20 MB.');
        await fileStorage.put(doc, file);
        return queue({ ...row, name: file.name, file_type: ext.toUpperCase(), mime_type: file.type, size_bytes: file.size, url: undefined });
    },
    async delete(bot: string, doc: string) { document(bot, doc); await delay(); await fileStorage.remove(doc); knowledgeRepository.remove(doc); toast('Knowledge source deleted.'); },
    async retry(bot: string, doc: string) { await delay(); return queue(document(bot, doc)); },
    async reindex(bot: string, doc: string) { return this.retry(bot, doc); },
};
