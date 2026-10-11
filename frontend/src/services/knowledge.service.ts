import { apiMode } from '@/config/data-source';
import { apiKnowledgeService } from './api/knowledge.api';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { fileStorage } from '@/storage/indexed-db';
import { requireBot, accessibleBots } from './access';
import { assertCanStore, warnIfNearLimit } from './quota.service';
import { syncKnowledgeStatus } from './bot-lifecycle';
import { notify } from './notification.service';
import { userRepository } from '@/repositories/auth.repository';
import { ApiError } from './api';
import { delay, id, now, required, toast } from '@/utils/demo';
import type { DocumentDetail, KnowledgeDocument } from '@/types/document';
import type { Chatbot } from '@/types/chatbot';
const extensions = ['pdf', 'docx', 'txt', 'md', 'xlsx'];
const MAX_BYTES = 20 * 1024 * 1024;
export type DuplicateAction = 'REPLACE' | 'SKIP';
export interface CrawlOptions { start_url: string; max_depth: number; max_pages: number }
export interface CrawlPage { url: string; title: string; content: string; status: 'OK' | 'EMPTY' | 'BLOCKED' }
export interface DocumentFilter { search?: string; type?: string; status?: string }
function document(bot: string, doc: string) {
    requireBot(bot);
    const row = knowledgeRepository.all().find(row => row.chatbot_id === bot && row.id === doc);
    if (!row)
        throw new Error('Knowledge source not found.');
    return row;
}
function validateFile(file: { name: string; size: number }) {
    const ext = file.name.split('.').pop()?.toLowerCase();
    if (!ext || !extensions.includes(ext))
        throw new ApiError(415, `"${file.name}" is not supported. Use PDF, DOCX, TXT, MD or XLSX.`);
    if (!file.size)
        throw new ApiError(400, `"${file.name}" is empty.`);
    if (file.size > MAX_BYTES)
        throw new ApiError(413, `"${file.name}" exceeds the 20 MB limit.`);
    return ext;
}
const sameName = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();
function owner(bot: Chatbot) { return userRepository.all().find(user => user.id === bot.ownerId); }
function fail(row: DocumentDetail, reason: string): DocumentDetail {
    const date = now();
    const failed: DocumentDetail = { ...row, status: 'FAILED', error_message: reason, chunkCount: 0, updated_at: date, jobs: [...row.jobs, { id: id('job'), status: 'FAILED', error_message: reason, created_at: date, updated_at: date }] };
    knowledgeRepository.put(failed);
    const bot = chatbotRepository.all().find(item => item.id === row.chatbot_id);
    if (bot)
        notify(bot.ownerId, row.url ? 'CRAWL_FAILED' : 'KB_PROCESSING_FAILED', row.url ? 'Website import failed' : 'Document processing failed', `${row.name}: ${reason}`, ['PUSH']);
    return failed;
}
// Derive stages from persisted timestamps so processing also resumes after a reload.
function progress(row: DocumentDetail): DocumentDetail {
    if (!row.processingStartedAt || ['READY', 'FAILED', 'REVIEW'].includes(row.status))
        return row;
    const stages: KnowledgeDocument['status'][] = row.url ? ['QUEUED', 'CRAWLING', 'EXTRACTING', 'CHUNKING', 'INDEXING', 'READY'] : ['QUEUED', 'UPLOADING', 'PROCESSING', 'CHUNKING', 'INDEXING', 'READY'];
    const step = Math.min(5, Math.floor((Date.now() - Date.parse(row.processingStartedAt)) / 900));
    // Demo trigger: names or URLs containing "corrupt" or "fail" end in FAILED so the failure UI can be explored.
    if (step >= 2 && /corrupt|fail/i.test(row.url ?? row.name))
        return fail(row, row.url ? 'The website could not be crawled (simulated failure).' : 'The file could not be read (simulated failure).');
    const status = stages[step];
    if (row.status === status)
        return row;
    const date = now();
    const updated = { ...row, status, updated_at: date, chunkCount: status === 'READY' ? Math.max(1, Math.ceil(row.size_bytes / 6000)) : 0,
        jobs: [...row.jobs, { id: id('job'), status, created_at: date, updated_at: date, error_message: null }] };
    knowledgeRepository.put(updated);
    if (status === 'READY') {
        syncKnowledgeStatus(row.chatbot_id);
        toast(`${row.name}: simulated processing complete.`);
    }
    return updated;
}
function queue(row: DocumentDetail) {
    const date = now();
    const updated: DocumentDetail = { ...row, status: 'QUEUED', chunkCount: 0, error_message: null, processingStartedAt: date, updated_at: date,
        jobs: [{ id: id('job'), status: 'QUEUED', error_message: null, created_at: date, updated_at: date }] };
    knowledgeRepository.put(updated);
    syncKnowledgeStatus(row.chatbot_id);
    return updated;
}
// Deterministic stand-in for a crawler: generates plausible pages for the start URL. Nothing is fetched.
function simulateCrawl(options: CrawlOptions): CrawlPage[] {
    const base = new URL(options.start_url);
    const paths = ['', 'about', 'pricing', 'faq', 'contact', 'blog', 'docs', 'support', 'careers', 'terms', 'privacy', 'team'];
    const perLevel = [1, 4, 8, 12][options.max_depth];
    return paths.slice(0, Math.min(perLevel, options.max_pages)).map((path, index) => {
        const url = new URL(path, base.href.endsWith('/') ? base.href : `${base.href}/`).href;
        const status: CrawlPage['status'] = /^(terms|privacy)$/.test(path) ? 'BLOCKED' : index === 5 ? 'EMPTY' : 'OK';
        return { url, title: path ? `${path[0].toUpperCase()}${path.slice(1)} – ${base.hostname}` : base.hostname, status,
            content: status === 'OK' ? `Simulated preview text for ${url}. The real page content is not fetched in the demo.` : '' };
    });
}
export const localKnowledgeService = {
    async availability(bot: string, doc: string) { document(bot, doc); return { available: !!(await fileStorage.get(doc)) }; },
    async policy() { return { max_upload_size_mb: 20, extensions, processing_enabled: true, worker_running: true }; },
    async recent() { const bots = accessibleBots(); return knowledgeRepository.all().filter(row => bots.some(bot => bot.id === row.chatbot_id)).map(progress).sort((a, b) => b.updated_at.localeCompare(a.updated_at)).slice(0, 10).map(row => ({ ...row, chatbot_name: bots.find(bot => bot.id === row.chatbot_id)!.name })); },
    async list(bot: string, filter: DocumentFilter = {}) {
        requireBot(bot);
        const search = filter.search?.trim().toLowerCase();
        return knowledgeRepository.all().filter(row => row.chatbot_id === bot).map(progress)
            .filter(row => (!search || row.name.toLowerCase().includes(search)) && (!filter.type || row.file_type === filter.type.toUpperCase()) && (!filter.status || row.status === filter.status));
    },
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
    /** Same file name in the same chatbot is a duplicate; the caller chooses Replace or Skip. */
    duplicateOf(botId: string, name: string) { requireBot(botId); return knowledgeRepository.all().find(row => row.chatbot_id === botId && sameName(row.name, name)); },
    async upload(botId: string, file: File, onDuplicate?: DuplicateAction) {
        const bot = requireBot(botId);
        const ext = validateFile(file);
        const duplicate = this.duplicateOf(botId, file.name);
        if (duplicate && !onDuplicate)
            throw new ApiError(409, `"${file.name}" already exists in this chatbot. Choose Replace or Skip.`);
        if (duplicate && onDuplicate === 'SKIP') {
            toast(`Skipped "${file.name}" (already uploaded).`);
            return duplicate as DocumentDetail;
        }
        const user = owner(bot);
        if (user)
            assertCanStore(user, file.size - (duplicate?.size_bytes ?? 0));
        const docId = duplicate?.id ?? id('doc');
        await fileStorage.put(docId, file);
        await delay();
        const date = now();
        const row = queue({ id: docId, ownerId: bot.ownerId, chatbot_id: botId, name: file.name, file_type: ext.toUpperCase(), mime_type: file.type, size_bytes: file.size, status: 'QUEUED', chunkCount: 0, error_message: null, created_at: duplicate?.created_at ?? date, updated_at: date, jobs: [] });
        if (user)
            warnIfNearLimit(user);
        toast(duplicate ? 'Document replaced.' : 'Document uploaded.');
        return row;
    },
    async website(botId: string, name: string, value: string) {
        const bot = requireBot(botId);
        const url = new URL(value);
        if (!['https:', 'http:'].includes(url.protocol))
            throw new Error('Use an HTTP or HTTPS website URL.');
        const user = owner(bot);
        if (user)
            assertCanStore(user, 48000);
        await delay();
        const date = now();
        return queue({ id: id('doc'), ownerId: bot.ownerId, chatbot_id: botId, name: required(name, 'Source name'), url: url.href, file_type: 'WEBSITE', mime_type: 'text/html', size_bytes: 48000, status: 'QUEUED', chunkCount: 0, error_message: null, created_at: date, updated_at: date, jobs: [] });
    },
    /** Step 1 of a site crawl: lists the pages that would be imported (simulated). Depth 1–3, 50–100 pages. */
    async crawlPreview(botId: string, options: CrawlOptions): Promise<CrawlPage[]> {
        requireBot(botId);
        let url: URL;
        try { url = new URL(options.start_url); } catch { throw new Error('Enter a valid start URL.'); }
        if (!['https:', 'http:'].includes(url.protocol))
            throw new Error('Use an HTTP or HTTPS start URL.');
        if (!Number.isInteger(options.max_depth) || options.max_depth < 1 || options.max_depth > 3)
            throw new Error('Max depth must be 1 to 3.');
        if (!Number.isInteger(options.max_pages) || options.max_pages < 50 || options.max_pages > 100)
            throw new Error('Max pages must be 50 to 100.');
        await delay(500);
        return simulateCrawl({ ...options, start_url: url.href });
    },
    /** Step 2: imports the previewed pages that were readable. */
    async importCrawl(botId: string, pages: CrawlPage[]) {
        const importable = pages.filter(page => page.status === 'OK');
        if (!importable.length)
            throw new Error('No readable pages to import.');
        const rows = [];
        for (const page of importable)
            rows.push(await this.website(botId, page.title, page.url));
        toast(`${rows.length} pages queued for import.`);
        return rows;
    },
    async rename(bot: string, doc: string, name: string) {
        const row = document(bot, doc);
        const next = required(name, 'Document name');
        if (knowledgeRepository.all().some(other => other.chatbot_id === bot && other.id !== doc && sameName(other.name, next)))
            throw new ApiError(409, 'Another document already uses that name.');
        const updated = { ...row, name: next, updated_at: now() };
        knowledgeRepository.put(updated);
        return updated;
    },
    async replace(bot: string, doc: string, file: File) {
        const row = document(bot, doc);
        const ext = validateFile(file);
        const user = owner(requireBot(bot));
        if (user)
            assertCanStore(user, file.size - row.size_bytes);
        await fileStorage.put(doc, file);
        return queue({ ...row, name: file.name, file_type: ext.toUpperCase(), mime_type: file.type, size_bytes: file.size, url: undefined });
    },
    async delete(bot: string, doc: string) { document(bot, doc); await delay(); await fileStorage.remove(doc); knowledgeRepository.remove(doc); syncKnowledgeStatus(bot); toast('Knowledge source deleted.'); },
    async retry(bot: string, doc: string) {
        const row = document(bot, doc);
        if (row.status !== 'FAILED')
            throw new ApiError(409, 'Only failed documents can be retried.');
        await delay();
        return queue(row);
    },
    async reindex(bot: string, doc: string) { const row = document(bot, doc); await delay(); return queue(row); },
};
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const knowledgeService: typeof localKnowledgeService = apiMode ? apiKnowledgeService : localKnowledgeService;
