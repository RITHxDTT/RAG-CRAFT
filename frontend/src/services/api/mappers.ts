// The Java services answer in snake_case JSON; these functions turn that into the types the screens already use.
import { newAccount } from '@/mocks/defaults';
import { userRepository, sessionRepository } from '@/repositories/auth.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import type { CurrentUser } from '@/types/auth';
import type { BotSettings, Chatbot, ChatbotInput } from '@/types/chatbot';
import type { DocumentDetail, KnowledgeDocument } from '@/types/document';
import type { UserAccount } from '@/types/demo';
import type { Channel } from '@/services/channel.service';
import type { Model, Prompt } from '@/services/catalog.service';
import type { MonitoredBot, UserRecord } from '@/services/admin.service';
type Dto = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any -- raw server JSON, validated by mapping
// ---------------------------------------------------------------- accounts
export function mapUser(dto: Dto): CurrentUser {
    return { id: dto.id, email: dto.email, role: dto.role, full_name: dto.full_name, display_name: dto.display_name ?? undefined, bio: dto.bio ?? undefined,
        avatar: dto.avatar ?? undefined, theme: dto.theme ?? undefined, language: dto.language, timezone: dto.timezone, organization_id: dto.organization_id ?? '',
        organization_name: dto.organization_name ?? '', pending_email: dto.pending_email ?? null, status: dto.status, signup_method: dto.signup_method,
        totp_enabled: false, mfa_required: false, passkey_count: 0, linked_accounts: [], deletion_requested_at: dto.deletion_requested_at ?? null };
}
/** Screens that are not on the database yet read the signed-in user from the local cache, so keep it in step with the server. */
export function cacheAccount(user: CurrentUser): UserAccount {
    const account = newAccount({ ...user, id: user.id, email: user.email, full_name: user.full_name, password: '', role: user.role, organization_id: user.organization_id,
        organization_name: user.organization_name, status: user.status ?? 'ACTIVE', is_active: user.status === 'ACTIVE', email_verified: true,
        signup_method: user.signup_method ?? 'EMAIL', pending_email: user.pending_email ?? null, deletion_requested_at: user.deletion_requested_at ?? null,
        builtIn: user.email === 'admin@gmail.com' || user.email === 'user@gmail.com' });
    userRepository.saveQuiet([...userRepository.all().filter(row => row.id !== account.id), account]);
    return account;
}
export function startSession(user: CurrentUser) {
    cacheAccount(user);
    sessionRepository.save({ userId: user.id, email: user.email, role: user.role, loggedIn: true });
}
// ---------------------------------------------------------------- chatbots
const LEGACY_FALLBACKS = ['I do not have that information.', 'I do not have that information in this demo knowledge base.', 'I do not have that information in this knowledge base.'];
export function mapSettings(dto: Dto): BotSettings {
    return { model_id: dto.model_id ?? null, model_name: dto.model_name, fallback_model_name: dto.fallback_model_name ?? null, embedding_model: dto.embedding_model,
        prompt_template_id: dto.prompt_template_id ?? null, system_instruction: dto.system_instruction ?? '', custom_instruction: dto.custom_instruction ?? '', tone: dto.tone,
        answer_length: dto.answer_length, formatting: dto.formatting, language: dto.language, temperature: dto.temperature, max_tokens: dto.max_tokens, top_k: dto.top_k,
        search_mode: dto.search_mode, max_context_tokens: dto.max_context_tokens, chunk_size: dto.chunk_size, chunk_overlap: dto.chunk_overlap,
        answer_from_documents_only: dto.answer_from_documents_only, welcome_message: dto.welcome_message ?? '', fallback_message: dto.fallback_message ?? '', show_citations: dto.show_citations };
}
export function mapBot(dto: Dto): Chatbot {
    return { id: dto.id, organization_id: dto.organization_id ?? '', ownerId: dto.owner_id, avatar: dto.avatar ?? undefined, name: dto.name, description: dto.description ?? '',
        starter_questions: dto.starter_questions ?? [], status: dto.status, disabled_reason: dto.disabled_reason ?? null, disabled_at: dto.disabled_at ?? null,
        created_at: dto.created_at, updated_at: dto.updated_at, settings: mapSettings(dto.settings ?? {}), document_count: dto.document_count ?? 0, ready_count: dto.ready_count ?? 0,
        failed_count: dto.failed_count ?? 0, channel_count: dto.channel_count ?? 0, kb_status: dto.kb_status };
}
/** Only the fields the form changed are sent; an unset value keeps the server's (admin-defined) default. */
export function botPayload(input: ChatbotInput) {
    const s = input.settings ?? {};
    const fallback = (s.fallback_message ?? '').trim();
    const settings = {
        model_id: s.model_id ?? undefined, prompt_template_id: s.prompt_template_id ?? undefined, embedding_model: s.embedding_model, system_instruction: s.system_instruction,
        custom_instruction: s.custom_instruction, tone: s.tone, answer_length: s.answer_length, formatting: s.formatting, language: s.language, temperature: s.temperature,
        max_tokens: s.max_tokens, top_k: s.top_k, search_mode: s.search_mode, max_context_tokens: s.max_context_tokens, chunk_size: s.chunk_size, chunk_overlap: s.chunk_overlap,
        answer_from_documents_only: s.answer_from_documents_only, welcome_message: s.welcome_message, show_citations: s.show_citations,
        // The old default text means "not customised"; the server then answers with the localized not-found message.
        fallback_message: s.fallback_message === undefined ? undefined : LEGACY_FALLBACKS.includes(fallback) ? '' : fallback,
    };
    return { name: input.name, description: input.description, avatar: input.avatar, status: input.status, starter_questions: input.starter_questions, settings: input.settings ? settings : undefined };
}
/** Local-only screens (playground, public chat, analytics) read chatbots from the cache, so refresh it after every server read. */
export function mirrorBots(bots: Chatbot[], replaceOwner?: string) {
    const ids = new Set(bots.map(bot => bot.id));
    const kept = chatbotRepository.all().filter(bot => !ids.has(bot.id) && (!replaceOwner || bot.ownerId !== replaceOwner));
    chatbotRepository.saveQuiet([...kept, ...bots]);
}
// ---------------------------------------------------------------- knowledge
export function mapDoc(dto: Dto): KnowledgeDocument {
    return { id: dto.id, ownerId: dto.owner_id, chatbot_id: dto.chatbot_id, chunkCount: dto.chunk_count ?? 0, url: dto.url ?? undefined, name: dto.name, file_type: dto.file_type,
        mime_type: dto.mime_type, size_bytes: dto.size_bytes, status: dto.status, error_message: dto.error_message ?? null, created_at: dto.created_at, updated_at: dto.updated_at };
}
export function mapDocDetail(dto: Dto): DocumentDetail {
    return { ...mapDoc(dto), jobs: (dto.jobs ?? []).map((job: Dto) => ({ id: job.id, status: job.status, error_message: job.error_message ?? null, created_at: job.created_at, updated_at: job.updated_at })) };
}
export function mirrorDocs(botId: string, docs: KnowledgeDocument[]) {
    const rows = [...knowledgeRepository.all().filter(doc => doc.chatbot_id !== botId), ...docs.map(doc => ({ ...doc, jobs: [] }))];
    knowledgeRepository.saveQuiet(rows as DocumentDetail[]);
}
// ---------------------------------------------------------------- channels
export function mapChannel(dto: Dto): Channel {
    const s = dto.settings ?? {};
    return { id: dto.id, ownerId: dto.owner_id, chatbot_id: dto.chatbot_id, channel: dto.channel, public_id: dto.public_id, enabled: dto.enabled, status: dto.status, url: dto.url ?? null,
        embed_code: dto.embed_code ?? null, telegram_username: dto.telegram_username ?? null, token_mask: dto.token_mask ?? null, error_reason: dto.error_reason ?? null,
        settings: { title: s.title ?? '', welcome: s.welcome ?? '', color: s.color ?? '#0d9488', position: s.position ?? 'BOTTOM_RIGHT', icon: s.icon ?? '💬', domains: s.domains ?? [],
            starters: s.starters ?? [], password: s.password ?? '', expires: s.expires ?? '' },
        created_at: dto.created_at, updated_at: dto.updated_at, messages_this_week: dto.messages_this_week ?? 0 };
}
export function mirrorChannels(botId: string, channels: Channel[]) {
    channelRepository.saveQuiet([...channelRepository.all().filter(row => row.chatbot_id !== botId), ...channels]);
}
// ---------------------------------------------------------------- catalog and admin
export const mapModel = (dto: Dto): Model => ({ id: dto.id, name: dto.name, provider: dto.provider, model_identifier: dto.model_identifier, kind: dto.kind, enabled: dto.enabled, is_default: dto.is_default });
export const mapPrompt = (dto: Dto): Prompt => ({ id: dto.id, name: dto.name, prompt: dto.prompt, enabled: dto.enabled });
export function mapAdminUser(dto: Dto): UserRecord {
    return { id: dto.id, full_name: dto.full_name, email: dto.email, role: dto.role, is_active: dto.is_active, builtIn: dto.built_in, created_at: dto.created_at, chatbot_count: dto.chatbot_count ?? 0,
        status: dto.status, signup_method: dto.signup_method, storage_used: dto.storage_used ?? 0, messages_this_month: 0, last_login_at: dto.last_login_at ?? null,
        quota: { max_bots: dto.quota?.max_bots ?? 5, max_storage_bytes: dto.quota?.max_storage_bytes ?? 0 }, over_quota: !!dto.over_quota, suspend_reason: dto.suspend_reason ?? null };
}
export const mapAdminBot = (dto: Dto): MonitoredBot => ({ id: dto.id, name: dto.name, status: dto.status, owner_email: dto.owner_email ?? '', owner_name: dto.owner_name ?? '', created_at: dto.created_at,
    knowledge_count: dto.knowledge_count, channel_count: dto.channel_count, disabled_reason: dto.disabled_reason ?? null });
