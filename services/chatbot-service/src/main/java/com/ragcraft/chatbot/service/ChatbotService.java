package com.ragcraft.chatbot.service;

import com.ragcraft.chatbot.api.ChatbotApi.AdminChatbotResponse;
import com.ragcraft.chatbot.api.ChatbotApi.ChatbotRequest;
import com.ragcraft.chatbot.api.ChatbotApi.ChatbotResponse;
import com.ragcraft.chatbot.api.ChatbotApi.DashboardResponse;
import com.ragcraft.chatbot.api.ChatbotApi.DocumentCounts;
import com.ragcraft.chatbot.api.ChatbotApi.HistoryResponse;
import com.ragcraft.chatbot.api.ChatbotApi.PublishChecklist;
import com.ragcraft.chatbot.api.ChatbotApi.SettingsRequest;
import com.ragcraft.chatbot.api.ChatbotApi.SettingsResponse;
import com.ragcraft.chatbot.api.ChatbotApi.StatsResponse;
import com.ragcraft.chatbot.client.CatalogClient;
import com.ragcraft.chatbot.client.IdentityClient;
import com.ragcraft.chatbot.client.RelatedServicesClient;
import com.ragcraft.chatbot.domain.Chatbot;
import com.ragcraft.chatbot.domain.ChatbotSettings;
import com.ragcraft.chatbot.domain.ChatbotStatusHistory;
import com.ragcraft.chatbot.repository.ChatbotRepository;
import com.ragcraft.chatbot.repository.ChatbotStatusHistoryRepository;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotService {

    /** Moves an owner or admin may make by hand. DRAFT and PENDING follow the knowledge base; DISABLED belongs to admins. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            Chatbot.DRAFT, Set.of(), Chatbot.PENDING, Set.of(Chatbot.ACTIVE), Chatbot.ACTIVE, Set.of(Chatbot.PAUSED),
            Chatbot.PAUSED, Set.of(Chatbot.ACTIVE), Chatbot.DISABLED, Set.of());
    private static final Set<String> TONES = Set.of("PROFESSIONAL", "FRIENDLY", "CASUAL", "FORMAL", "CUSTOM");
    private static final Set<String> LENGTHS = Set.of("CONCISE", "DETAILED");
    private static final Set<String> FORMATS = Set.of("RICH", "PLAIN");
    private static final Set<String> LANGUAGES = Set.of("AUTO", "EN", "KM", "KO");
    private static final Set<String> MODES = Set.of("SEMANTIC", "KEYWORD", "HYBRID");

    private final ChatbotRepository chatbots;
    private final ChatbotStatusHistoryRepository history;
    private final CatalogClient catalog;
    private final IdentityClient identity;
    private final RelatedServicesClient related;

    public ChatbotService(ChatbotRepository chatbots, ChatbotStatusHistoryRepository history, CatalogClient catalog,
                          IdentityClient identity, RelatedServicesClient related) {
        this.chatbots = chatbots;
        this.history = history;
        this.catalog = catalog;
        this.identity = identity;
        this.related = related;
    }

    /** List filters: bot name search, status, and sort (UPDATED, NAME_ASC, NAME_DESC). */
    public record Query(String search, String status, String sort) {}

    // ------------------------------------------------------------------ reads

    @Transactional
    public List<ChatbotResponse> list(UserPrincipal principal, Query query) {
        List<Chatbot> rows = principal.isAdmin() ? chatbots.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 500))
                : chatbots.findByOwnerIdOrderByUpdatedAtDesc(principal.userId());
        String search = query.search() == null ? "" : query.search().trim().toLowerCase();
        List<ChatbotResponse> result = withCounts(rows).stream()
                .filter(bot -> search.isEmpty() || bot.name().toLowerCase().contains(search))
                .filter(bot -> query.status() == null || query.status().isBlank() || query.status().equalsIgnoreCase(bot.status()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        String sort = query.sort() == null ? "UPDATED" : query.sort();
        if ("NAME_ASC".equals(sort)) result.sort(Comparator.comparing(b -> b.name().toLowerCase()));
        else if ("NAME_DESC".equals(sort)) result.sort(Comparator.comparing((ChatbotResponse b) -> b.name().toLowerCase()).reversed());
        return result;
    }

    @Transactional
    public ChatbotResponse get(UUID id, UserPrincipal principal) {
        return withCounts(List.of(require(id, principal))).get(0);
    }

    @Transactional
    public PublishChecklist checklist(UUID id, UserPrincipal principal) {
        return checklistFor(require(id, principal));
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> history(UUID id, UserPrincipal principal) {
        require(id, principal);
        return history.findByChatbotIdOrderByCreatedAtDesc(id).stream()
                .map(h -> new HistoryResponse(h.getFromStatus(), h.getToStatus(), h.getActorRole(), h.getReason(), h.getCreatedAt())).toList();
    }

    // ------------------------------------------------------------------ create / update

    @Transactional
    public ChatbotResponse create(ChatbotRequest request, UserPrincipal principal) {
        if (request.name() == null || request.name().isBlank()) throw AppException.badRequest("Name is required.");
        assertWithinQuota(principal.userId());
        Chatbot bot = new Chatbot();
        bot.setOwnerId(principal.userId());
        bot.setOrganizationId(principal.organizationId());
        bot.setSettings(new ChatbotSettings());
        applyDetails(bot, request);
        applySettings(bot.getSettings(), request.settings(), true);
        // A new chatbot always starts as DRAFT, whatever status was requested; it becomes PENDING when a document is indexed.
        bot.setStatus(Chatbot.DRAFT);
        Chatbot saved = chatbots.save(bot);
        history.save(ChatbotStatusHistory.of(saved.getId(), null, Chatbot.DRAFT, principal.userId(), actor(principal), "Created"));
        return withCounts(List.of(saved)).get(0);
    }

    @Transactional
    public ChatbotResponse update(UUID id, ChatbotRequest request, UserPrincipal principal) {
        Chatbot bot = require(id, principal);
        applyDetails(bot, request);
        if (request.settings() != null) applySettings(bot.getSettings(), request.settings(), false);
        if (request.status() != null) {
            refreshKnowledgeStatus(bot);
            if (!request.status().equalsIgnoreCase(bot.getStatus())) transition(bot, request.status().toUpperCase(), principal, null);
        }
        return withCounts(List.of(chatbots.save(bot))).get(0);
    }

    @Transactional
    public ChatbotResponse publish(UUID id, UserPrincipal principal) { return move(id, Chatbot.ACTIVE, principal); }

    @Transactional
    public ChatbotResponse pause(UUID id, UserPrincipal principal) { return move(id, Chatbot.PAUSED, principal); }

    @Transactional
    public ChatbotResponse resume(UUID id, UserPrincipal principal) { return move(id, Chatbot.ACTIVE, principal); }

    private ChatbotResponse move(UUID id, String next, UserPrincipal principal) {
        Chatbot bot = require(id, principal);
        refreshKnowledgeStatus(bot);
        if (!next.equals(bot.getStatus())) transition(bot, next, principal, null);
        return withCounts(List.of(chatbots.save(bot))).get(0);
    }

    @Transactional
    public ChatbotResponse duplicate(UUID id, UserPrincipal principal) {
        Chatbot source = require(id, principal);
        assertWithinQuota(principal.userId());
        Chatbot copy = new Chatbot();
        copy.setOwnerId(source.getOwnerId());
        copy.setOrganizationId(source.getOrganizationId());
        String suffix = " (copy)";
        copy.setName(source.getName().length() + suffix.length() > 50 ? source.getName().substring(0, 50 - suffix.length()) + suffix : source.getName() + suffix);
        copy.setDescription(source.getDescription());
        copy.setAvatar(source.getAvatar());
        copy.setStatus(Chatbot.DRAFT);
        copy.setStarterQuestions(source.getStarterQuestions());
        ChatbotSettings settings = new ChatbotSettings();
        ChatbotSettings from = source.getSettings();
        settings.setModelId(from.getModelId());
        settings.setModelName(from.getModelName());
        settings.setFallbackModelId(from.getFallbackModelId());
        settings.setFallbackModelName(from.getFallbackModelName());
        settings.setEmbeddingModel(from.getEmbeddingModel());
        settings.setPromptTemplateId(from.getPromptTemplateId());
        settings.setSystemInstruction(from.getSystemInstruction());
        settings.setCustomInstruction(from.getCustomInstruction());
        settings.setTone(from.getTone());
        settings.setAnswerLength(from.getAnswerLength());
        settings.setFormatting(from.getFormatting());
        settings.setLanguage(from.getLanguage());
        settings.setTemperature(from.getTemperature());
        settings.setMaxTokens(from.getMaxTokens());
        settings.setTopK(from.getTopK());
        settings.setSearchMode(from.getSearchMode());
        settings.setMaxContextTokens(from.getMaxContextTokens());
        settings.setChunkSize(from.getChunkSize());
        settings.setChunkOverlap(from.getChunkOverlap());
        settings.setAnswerFromDocumentsOnly(from.isAnswerFromDocumentsOnly());
        settings.setWelcomeMessage(from.getWelcomeMessage());
        settings.setFallbackMessage(from.getFallbackMessage());
        settings.setShowCitations(from.isShowCitations());
        copy.setSettings(settings);
        Chatbot saved = chatbots.save(copy);
        history.save(ChatbotStatusHistory.of(saved.getId(), null, Chatbot.DRAFT, principal.userId(), actor(principal), "Duplicated from " + source.getName()));
        return withCounts(List.of(saved)).get(0);
    }

    /** Deleting needs the chatbot name retyped. Guests then see "This chatbot is no longer available!". */
    @Transactional
    public void delete(UUID id, String confirmName, UserPrincipal principal) {
        Chatbot bot = require(id, principal);
        if (confirmName == null || !confirmName.trim().equals(bot.getName())) {
            throw AppException.badRequest("Type the chatbot name exactly to confirm deletion.");
        }
        if (related.telegramConnected(bot.getId())) {
            throw AppException.conflict("Disconnect Telegram before deleting this chatbot.");
        }
        related.cascadeDelete(bot.getId());
        chatbots.delete(bot);
    }

    /** Called by identity-service when a user account is removed. */
    @Transactional
    public void deleteOwner(UUID ownerId) {
        for (Chatbot bot : chatbots.findByOwnerIdOrderByUpdatedAtDesc(ownerId)) {
            related.cascadeDelete(bot.getId());
            chatbots.delete(bot);
        }
    }

    // ------------------------------------------------------------------ dashboards and internal reads

    @Transactional
    public DashboardResponse dashboard(UserPrincipal principal) {
        List<Chatbot> rows = principal.isAdmin() ? chatbots.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 500))
                : chatbots.findByOwnerIdOrderByUpdatedAtDesc(principal.userId());
        List<ChatbotResponse> all = withCounts(rows);
        long total = all.stream().mapToLong(ChatbotResponse::documentCount).sum();
        long ready = all.stream().mapToLong(ChatbotResponse::readyCount).sum();
        long failed = all.stream().mapToLong(ChatbotResponse::failedCount).sum();
        long active = all.stream().filter(bot -> Chatbot.ACTIVE.equals(bot.status())).count();
        return new DashboardResponse(all.size(), active, total, ready, failed, all.stream().limit(6).toList());
    }

    @Transactional(readOnly = true)
    public List<AdminChatbotResponse> adminList(int offset, int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        List<Chatbot> rows = chatbots.findAllByOrderByUpdatedAtDesc(PageRequest.of(offset / size, size));
        List<UUID> ids = rows.stream().map(Chatbot::getId).toList();
        Map<UUID, DocumentCounts> docs = related.documentCounts(ids);
        Map<UUID, Long> channels = related.channelCounts(ids);
        Map<UUID, RelatedServicesClient.OwnerSummary> owners = related.owners(rows.stream().map(Chatbot::getOwnerId).distinct().toList());
        return rows.stream().map(bot -> {
            RelatedServicesClient.OwnerSummary owner = owners.get(bot.getOwnerId());
            return new AdminChatbotResponse(bot.getId(), bot.getName(), bot.getStatus(), bot.getDisabledReason(), bot.getOwnerId(),
                    owner == null ? null : owner.email(), owner == null ? null : owner.fullName(),
                    docs.getOrDefault(bot.getId(), DocumentCounts.zero()).total(), channels.getOrDefault(bot.getId(), 0L), bot.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(UUID ownerId) {
        return ownerId == null
                ? new StatsResponse(chatbots.count(), chatbots.countByStatus(Chatbot.ACTIVE))
                : new StatsResponse(chatbots.countByOwnerId(ownerId), chatbots.countByOwnerIdAndStatus(ownerId, Chatbot.ACTIVE));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> countByOwners(List<UUID> ownerIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (ownerIds.isEmpty()) return result;
        for (Object[] row : chatbots.countByOwners(ownerIds)) {
            result.put((UUID) row[0], (Long) row[1]);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ChatbotSummary summary(UUID id) {
        return toSummary(chatbots.findById(id).orElseThrow(() -> AppException.notFound("Chatbot not found.")));
    }

    @Transactional(readOnly = true)
    public List<ChatbotSummary> summaries(UUID ownerId) {
        List<Chatbot> rows = ownerId == null ? chatbots.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 1000))
                : chatbots.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
        return rows.stream().map(ChatbotService::toSummary).toList();
    }

    // ------------------------------------------------------------------ rules

    Chatbot require(UUID id, UserPrincipal principal) {
        Chatbot bot = chatbots.findById(id).orElseThrow(() -> AppException.notFound("Chatbot not found."));
        if (!principal.canAccess(bot.getOwnerId())) throw AppException.notFound("Chatbot not found.");
        return bot;
    }

    private static String actor(UserPrincipal principal) { return principal.isAdmin() ? "ADMIN" : "OWNER"; }

    private void assertWithinQuota(UUID ownerId) {
        IdentityClient.Quota quota = identity.quota(ownerId);
        if (chatbots.countByOwnerId(ownerId) >= quota.maxBots()) {
            throw AppException.withCode(HttpStatus.FORBIDDEN,
                    "Chatbot limit reached (" + quota.maxBots() + "). Delete a chatbot or ask an administrator to raise your quota.", "QUOTA_BOTS");
        }
    }

    /** One place decides every status move, so the REST API, the form and the quick buttons all obey the same rules. */
    void transition(Chatbot bot, String next, UserPrincipal actor, String reason) {
        if (Chatbot.DISABLED.equals(bot.getStatus())) {
            throw AppException.withCode(HttpStatus.FORBIDDEN, "This chatbot was disabled by an administrator. Submit an appeal to request a review.", "DISABLED");
        }
        if (!ALLOWED.getOrDefault(bot.getStatus(), Set.of()).contains(next)) {
            String message = Chatbot.ACTIVE.equals(next) && Chatbot.DRAFT.equals(bot.getStatus())
                    ? "Add at least one document and wait for indexing before publishing."
                    : "A " + bot.getStatus().toLowerCase() + " chatbot cannot move to " + next.toLowerCase() + ".";
            throw AppException.withCode(HttpStatus.CONFLICT, message, "BAD_TRANSITION");
        }
        if (Chatbot.ACTIVE.equals(next)) {
            PublishChecklist checklist = checklistFor(bot);
            if (!checklist.ready()) {
                List<String> missing = new java.util.ArrayList<>();
                if (!checklist.hasDocument()) missing.add("at least one ready document");
                if (!checklist.modelSelected()) missing.add("a model");
                if (!checklist.channelReady()) missing.add("one channel");
                throw AppException.withCode(HttpStatus.CONFLICT, "Before publishing you need " + String.join(", ", missing) + ".", "CHECKLIST");
            }
        }
        record(bot, bot.getStatus(), next, actor.userId(), actor(actor), reason);
        bot.setStatus(next);
    }

    /** A document may have finished indexing since the status was last stored, so bring DRAFT/PENDING up to date before a manual move. */
    private void refreshKnowledgeStatus(Chatbot bot) {
        Map<UUID, DocumentCounts> counts = related.documentCountsOrNull(List.of(bot.getId()));
        if (counts != null) syncKnowledgeStatus(bot, counts.getOrDefault(bot.getId(), DocumentCounts.zero()));
    }

    private void record(Chatbot bot, String from, String to, UUID by, String role, String reason) {
        history.save(ChatbotStatusHistory.of(bot.getId(), from, to, by, role, reason));
    }

    private PublishChecklist checklistFor(Chatbot bot) {
        Map<UUID, DocumentCounts> documents = related.documentCountsOrNull(List.of(bot.getId()));
        Map<UUID, Long> channels = related.channelCountsOrNull(List.of(bot.getId()));
        if (documents == null || channels == null) {
            throw AppException.withCode(HttpStatus.SERVICE_UNAVAILABLE, "The document or channel service is unavailable, so the publish checklist cannot be checked. Try again shortly.", "DEPENDENCY_DOWN");
        }
        DocumentCounts counts = documents.getOrDefault(bot.getId(), DocumentCounts.zero());
        boolean channel = channels.getOrDefault(bot.getId(), 0L) > 0;
        boolean model = bot.getSettings().getModelName() != null && !bot.getSettings().getModelName().isBlank();
        boolean doc = counts.ready() > 0;
        return new PublishChecklist(doc, model, channel, doc && model && channel);
    }

    /** DRAFT becomes PENDING once a document is READY, and falls back to DRAFT if none remain. */
    private void syncKnowledgeStatus(Chatbot bot, DocumentCounts counts) {
        String next = Chatbot.DRAFT.equals(bot.getStatus()) && counts.ready() > 0 ? Chatbot.PENDING
                : Chatbot.PENDING.equals(bot.getStatus()) && counts.ready() == 0 ? Chatbot.DRAFT : null;
        if (next == null) return;
        record(bot, bot.getStatus(), next, null, "SYSTEM", counts.ready() > 0 ? "A document finished indexing" : "No ready documents remain");
        bot.setStatus(next);
        chatbots.save(bot);
    }

    private void applyDetails(Chatbot bot, ChatbotRequest request) {
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) throw AppException.badRequest("Name is required.");
            if (name.length() > 50) throw AppException.badRequest("Chatbot name must be 50 characters or fewer.");
            bot.setName(name);
        }
        if (request.description() != null) {
            if (request.description().length() > 200) throw AppException.badRequest("Description must be 200 characters or fewer.");
            bot.setDescription(request.description().trim());
        }
        if (request.avatar() != null) bot.setAvatar(request.avatar().isBlank() ? null : request.avatar().trim());
        if (request.starterQuestions() != null) {
            List<String> questions = request.starterQuestions().stream().map(String::trim).filter(q -> !q.isBlank()).toList();
            if (questions.size() > 4) throw AppException.badRequest("Add up to 4 starter questions.");
            bot.setStarterQuestions(questions);
        }
    }

    private void applySettings(ChatbotSettings s, SettingsRequest in, boolean creating) {
        SettingsRequest incoming = in == null ? new SettingsRequest(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null) : in;
        if (incoming.modelId() != null) {
            CatalogClient.Model model = catalog.model(incoming.modelId());
            s.setModelId(model.id());
            s.setModelName(model.modelIdentifier());
        } else if (creating) {
            CatalogClient.Model model = catalog.defaultModel();
            s.setModelId(model.id());
            s.setModelName(model.modelIdentifier());
        }
        if (Boolean.TRUE.equals(incoming.clearFallbackModel())) {
            s.setFallbackModelId(null);
            s.setFallbackModelName(null);
        } else if (incoming.fallbackModelId() != null) {
            CatalogClient.Model fallback = catalog.model(incoming.fallbackModelId());
            s.setFallbackModelId(fallback.id());
            s.setFallbackModelName(fallback.modelIdentifier());
        }
        if (s.getFallbackModelName() != null && s.getFallbackModelName().equals(s.getModelName())) {
            throw AppException.badRequest("The fallback model must differ from the primary model.");
        }
        applyEmbedding(s, incoming.embeddingModel(), creating);
        if (incoming.promptTemplateId() != null) s.setPromptTemplateId(catalog.prompt(incoming.promptTemplateId()).id());
        if (incoming.systemInstruction() != null && !incoming.systemInstruction().isBlank()) s.setSystemInstruction(incoming.systemInstruction());
        if (incoming.customInstruction() != null) s.setCustomInstruction(incoming.customInstruction());
        s.setTone(oneOf(incoming.tone(), TONES, s.getTone(), "Unknown tone."));
        if ("CUSTOM".equals(s.getTone()) && (s.getCustomInstruction() == null || s.getCustomInstruction().isBlank())) {
            throw AppException.badRequest("Describe the custom tone.");
        }
        s.setAnswerLength(oneOf(incoming.answerLength(), LENGTHS, s.getAnswerLength(), "Unknown answer length."));
        s.setFormatting(oneOf(incoming.formatting(), FORMATS, s.getFormatting(), "Unknown formatting."));
        s.setLanguage(oneOf(incoming.language(), LANGUAGES, s.getLanguage(), "Unknown language."));
        s.setSearchMode(oneOf(incoming.searchMode(), MODES, s.getSearchMode(), "Unknown search mode."));
        Map<String, CatalogClient.Limit> limits = catalog.limits();
        s.setTemperature(pick("temperature", incoming.temperature(), s.getTemperature(), creating, limits));
        s.setMaxTokens((int) pick("max_tokens", dbl(incoming.maxTokens()), s.getMaxTokens(), creating, limits));
        s.setTopK((int) pick("top_k", dbl(incoming.topK()), s.getTopK(), creating, limits));
        s.setMaxContextTokens((int) pick("max_context_tokens", dbl(incoming.maxContextTokens()), s.getMaxContextTokens(), creating, limits));
        s.setChunkSize((int) pick("chunk_size", dbl(incoming.chunkSize()), s.getChunkSize(), creating, limits));
        s.setChunkOverlap((int) pick("chunk_overlap", dbl(incoming.chunkOverlap()), s.getChunkOverlap(), creating, limits));
        if (s.getChunkOverlap() >= s.getChunkSize()) throw AppException.badRequest("Chunk overlap must be smaller than the chunk size.");
        if (incoming.answerFromDocumentsOnly() != null) s.setAnswerFromDocumentsOnly(incoming.answerFromDocumentsOnly());
        if (incoming.welcomeMessage() != null) s.setWelcomeMessage(incoming.welcomeMessage());
        if (incoming.fallbackMessage() != null) s.setFallbackMessage(incoming.fallbackMessage().isBlank() ? null : incoming.fallbackMessage());
        if (incoming.showCitations() != null) s.setShowCitations(incoming.showCitations());
    }

    private void applyEmbedding(ChatbotSettings s, String requested, boolean creating) {
        if (!creating) {
            if (requested != null && !requested.isBlank() && !requested.equals(s.getEmbeddingModel())) {
                throw AppException.badRequest("The embedding model cannot be changed after the chatbot is created.");
            }
            return;
        }
        if (requested == null || requested.isBlank()) {
            s.setEmbeddingModel(catalog.defaultEmbeddingModel().modelIdentifier());
            return;
        }
        boolean known = catalog.embeddingModels().stream().anyMatch(m -> m.modelIdentifier().equals(requested) || m.name().equals(requested));
        if (!known) throw AppException.badRequest("Choose an available embedding model.");
        s.setEmbeddingModel(catalog.embeddingModels().stream().filter(m -> m.modelIdentifier().equals(requested) || m.name().equals(requested))
                .findFirst().orElseThrow().modelIdentifier());
    }

    private static Double dbl(Integer value) { return value == null ? null : value.doubleValue(); }

    private static String oneOf(String value, Set<String> allowed, String current, String message) {
        if (value == null) return current;
        String normalized = value.toUpperCase();
        if (!allowed.contains(normalized)) throw AppException.badRequest(message);
        return normalized;
    }

    /**
     * Applies the admin-defined limits: a value must sit inside [min, max]. When nothing is sent, a new chatbot gets the admin's
     * default and an existing one keeps its value, clamped into range so tightening a limit never blocks unrelated edits.
     */
    private static double pick(String key, Double incoming, double current, boolean creating, Map<String, CatalogClient.Limit> limits) {
        CatalogClient.Limit limit = limits.get(key);
        if (limit == null) return incoming != null ? incoming : current;
        double min = limit.min().doubleValue(), max = limit.max().doubleValue();
        if (incoming != null) {
            if (incoming < min || incoming > max) {
                throw AppException.badRequest(label(key) + " must be between " + plain(limit.min()) + " and " + plain(limit.max()) + ".");
            }
            return incoming;
        }
        return creating ? limit.defaultValue().doubleValue() : Math.min(max, Math.max(min, current));
    }

    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private static String label(String key) {
        return switch (key) {
            case "temperature" -> "Temperature";
            case "top_k" -> "Top K results";
            case "max_tokens" -> "Max response length";
            case "max_context_tokens" -> "Max context size";
            case "chunk_size" -> "Chunk size";
            default -> "Chunk overlap";
        };
    }

    // ------------------------------------------------------------------ mapping

    private List<ChatbotResponse> withCounts(List<Chatbot> rows) {
        List<UUID> ids = rows.stream().map(Chatbot::getId).toList();
        Map<UUID, DocumentCounts> counts = related.documentCountsOrNull(ids);
        Map<UUID, Long> channels = related.channelCounts(ids);
        return rows.stream().map(bot -> {
            DocumentCounts docs = counts == null ? DocumentCounts.zero() : counts.getOrDefault(bot.getId(), DocumentCounts.zero());
            // Status follows the knowledge base, but only when knowledge-service actually answered.
            if (counts != null) syncKnowledgeStatus(bot, docs);
            return toResponse(bot, docs, channels.getOrDefault(bot.getId(), 0L));
        }).toList();
    }

    static ChatbotResponse toResponse(Chatbot bot, DocumentCounts counts, long channelCount) {
        ChatbotSettings s = bot.getSettings();
        SettingsResponse settings = new SettingsResponse(s.getModelId(), s.getModelName(), s.getFallbackModelId(), s.getFallbackModelName(),
                s.getEmbeddingModel(), s.getPromptTemplateId(), s.getSystemInstruction(), s.getCustomInstruction(), s.getTone(), s.getAnswerLength(),
                s.getFormatting(), s.getLanguage(), s.getTemperature(), s.getMaxTokens(), s.getTopK(), s.getSearchMode(), s.getMaxContextTokens(),
                s.getChunkSize(), s.getChunkOverlap(), s.isAnswerFromDocumentsOnly(), s.getWelcomeMessage(), s.getFallbackMessage(), s.isShowCitations());
        String kb = counts.total() == 0 ? "EMPTY" : counts.failed() > 0 ? "FAILED" : counts.ready() == counts.total() ? "READY" : "PROCESSING";
        return new ChatbotResponse(bot.getId(), bot.getOrganizationId(), bot.getOwnerId(), bot.getName(), bot.getDescription(), bot.getAvatar(),
                bot.getStatus(), bot.getDisabledReason(), bot.getDisabledAt(), List.copyOf(bot.getStarterQuestions()), settings,
                counts.total(), counts.ready(), counts.failed(), kb, channelCount, bot.getCreatedAt(), bot.getUpdatedAt());
    }

    static ChatbotSummary toSummary(Chatbot bot) {
        ChatbotSettings s = bot.getSettings();
        return new ChatbotSummary(bot.getId(), bot.getOrganizationId(), bot.getOwnerId(), bot.getName(), bot.getDescription(), bot.getAvatar(),
                bot.getStatus(), List.copyOf(bot.getStarterQuestions()),
                new ChatbotSummary.Settings(s.getModelId(), s.getModelName(), s.getPromptTemplateId(), s.getSystemInstruction(), s.getCustomInstruction(),
                        s.getTone(), s.getTemperature(), s.getAnswerLength(), s.getTopK(), s.getWelcomeMessage(), s.getFallbackMessage(), s.isShowCitations()));
    }
}
