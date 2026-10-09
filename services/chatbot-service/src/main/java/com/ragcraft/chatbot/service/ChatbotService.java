package com.ragcraft.chatbot.service;

import com.ragcraft.chatbot.api.ChatbotApi.AdminChatbotResponse;
import com.ragcraft.chatbot.api.ChatbotApi.ChatbotRequest;
import com.ragcraft.chatbot.api.ChatbotApi.ChatbotResponse;
import com.ragcraft.chatbot.api.ChatbotApi.DashboardResponse;
import com.ragcraft.chatbot.api.ChatbotApi.DocumentCounts;
import com.ragcraft.chatbot.api.ChatbotApi.SettingsRequest;
import com.ragcraft.chatbot.api.ChatbotApi.SettingsResponse;
import com.ragcraft.chatbot.api.ChatbotApi.StatsResponse;
import com.ragcraft.chatbot.client.CatalogClient;
import com.ragcraft.chatbot.client.RelatedServicesClient;
import com.ragcraft.chatbot.domain.Chatbot;
import com.ragcraft.chatbot.domain.ChatbotSettings;
import com.ragcraft.chatbot.repository.ChatbotRepository;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import com.ragcraft.common.web.Json;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotService {

    private static final Set<String> STATUSES = Set.of(Chatbot.DRAFT, Chatbot.ACTIVE, Chatbot.INACTIVE, Chatbot.ERROR);
    private static final Set<String> TONES = Set.of("PROFESSIONAL", "FRIENDLY", "CONCISE", "EDUCATIONAL", "DETAILED");
    private static final Set<String> LENGTHS = Set.of("SHORT", "MEDIUM", "LONG");

    private final ChatbotRepository chatbots;
    private final CatalogClient catalog;
    private final RelatedServicesClient related;

    public ChatbotService(ChatbotRepository chatbots, CatalogClient catalog, RelatedServicesClient related) {
        this.chatbots = chatbots;
        this.catalog = catalog;
        this.related = related;
    }

    @Transactional(readOnly = true)
    public List<ChatbotResponse> list(UserPrincipal principal) {
        List<Chatbot> rows = principal.isAdmin() ? chatbots.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 500))
                : chatbots.findByOwnerIdOrderByUpdatedAtDesc(principal.userId());
        return withCounts(rows);
    }

    @Transactional(readOnly = true)
    public ChatbotResponse get(UUID id, UserPrincipal principal) {
        return withCounts(List.of(require(id, principal))).get(0);
    }

    @Transactional
    public ChatbotResponse create(ChatbotRequest request, UserPrincipal principal) {
        if (request.name() == null || request.name().isBlank()) throw AppException.badRequest("Name is required.");
        Chatbot bot = new Chatbot();
        bot.setOwnerId(principal.userId());
        bot.setOrganizationId(principal.organizationId());
        bot.setSettings(new ChatbotSettings());
        apply(bot, request, true);
        return withCounts(List.of(chatbots.save(bot))).get(0);
    }

    @Transactional
    public ChatbotResponse update(UUID id, ChatbotRequest request, UserPrincipal principal) {
        Chatbot bot = require(id, principal);
        apply(bot, request, false);
        return withCounts(List.of(chatbots.save(bot))).get(0);
    }

    @Transactional
    public ChatbotResponse duplicate(UUID id, UserPrincipal principal) {
        Chatbot source = require(id, principal);
        Chatbot copy = new Chatbot();
        copy.setOwnerId(source.getOwnerId());
        copy.setOrganizationId(source.getOrganizationId());
        copy.setName((source.getName() + " (copy)").length() > 120 ? source.getName().substring(0, 113) + " (copy)" : source.getName() + " (copy)");
        copy.setDescription(source.getDescription());
        copy.setAvatar(source.getAvatar());
        copy.setStatus(Chatbot.DRAFT);
        copy.setStarterQuestions(source.getStarterQuestions());
        ChatbotSettings settings = new ChatbotSettings();
        ChatbotSettings from = source.getSettings();
        settings.setModelId(from.getModelId());
        settings.setModelName(from.getModelName());
        settings.setPromptTemplateId(from.getPromptTemplateId());
        settings.setSystemInstruction(from.getSystemInstruction());
        settings.setCustomInstruction(from.getCustomInstruction());
        settings.setTone(from.getTone());
        settings.setTemperature(from.getTemperature());
        settings.setAnswerLength(from.getAnswerLength());
        settings.setTopK(from.getTopK());
        settings.setWelcomeMessage(from.getWelcomeMessage());
        settings.setFallbackMessage(from.getFallbackMessage());
        settings.setShowCitations(from.isShowCitations());
        copy.setSettings(settings);
        return withCounts(List.of(chatbots.save(copy))).get(0);
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Chatbot bot = require(id, principal);
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

    @Transactional(readOnly = true)
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
            return new AdminChatbotResponse(bot.getId(), bot.getName(), bot.getStatus(), bot.getOwnerId(),
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

    // ----- helpers -----

    Chatbot require(UUID id, UserPrincipal principal) {
        Chatbot bot = chatbots.findById(id).orElseThrow(() -> AppException.notFound("Chatbot not found."));
        if (!principal.canAccess(bot.getOwnerId())) throw AppException.notFound("Chatbot not found.");
        return bot;
    }

    private void apply(Chatbot bot, ChatbotRequest request, boolean creating) {
        if (request.name() != null) bot.setName(request.name().trim());
        if (request.description() != null) bot.setDescription(request.description().trim());
        if (request.avatar() != null) bot.setAvatar(request.avatar().isBlank() ? null : request.avatar().trim());
        if (request.status() != null) {
            String status = request.status().toUpperCase();
            if (!STATUSES.contains(status)) throw AppException.badRequest("Unknown chatbot status.");
            bot.setStatus(status);
        }
        if (request.starterQuestions() != null) {
            bot.setStarterQuestions(Json.write(request.starterQuestions().stream().map(String::trim).filter(q -> !q.isBlank()).toList()));
        }
        ChatbotSettings settings = bot.getSettings();
        SettingsRequest incoming = request.settings();
        if (incoming != null) {
            if (incoming.modelId() != null) {
                CatalogClient.Model model = catalog.model(incoming.modelId());
                settings.setModelId(model.id());
                settings.setModelName(model.modelIdentifier());
            }
            if (incoming.promptTemplateId() != null) {
                settings.setPromptTemplateId(catalog.prompt(incoming.promptTemplateId()).id());
            }
            if (incoming.systemInstruction() != null && !incoming.systemInstruction().isBlank()) settings.setSystemInstruction(incoming.systemInstruction());
            if (incoming.customInstruction() != null) settings.setCustomInstruction(incoming.customInstruction());
            if (incoming.tone() != null) {
                String tone = incoming.tone().toUpperCase();
                if (!TONES.contains(tone)) throw AppException.badRequest("Unknown tone.");
                settings.setTone(tone);
            }
            if (incoming.temperature() != null) settings.setTemperature(incoming.temperature());
            if (incoming.answerLength() != null) {
                String length = incoming.answerLength().toUpperCase();
                if (!LENGTHS.contains(length)) throw AppException.badRequest("Unknown answer length.");
                settings.setAnswerLength(length);
            }
            if (incoming.topK() != null) settings.setTopK(incoming.topK());
            if (incoming.welcomeMessage() != null) settings.setWelcomeMessage(incoming.welcomeMessage());
            if (incoming.fallbackMessage() != null) settings.setFallbackMessage(incoming.fallbackMessage());
            if (incoming.showCitations() != null) settings.setShowCitations(incoming.showCitations());
        }
        if (creating && settings.getModelName() == null) {
            CatalogClient.Model model = catalog.defaultModel();
            settings.setModelId(model.id());
            settings.setModelName(model.modelIdentifier());
        }
    }

    private List<ChatbotResponse> withCounts(List<Chatbot> rows) {
        Map<UUID, DocumentCounts> counts = related.documentCounts(rows.stream().map(Chatbot::getId).toList());
        return rows.stream().map(bot -> toResponse(bot, counts.getOrDefault(bot.getId(), DocumentCounts.zero()))).toList();
    }

    static ChatbotResponse toResponse(Chatbot bot, DocumentCounts counts) {
        ChatbotSettings s = bot.getSettings();
        SettingsResponse settings = new SettingsResponse(s.getModelId(), s.getModelName(), s.getPromptTemplateId(), s.getSystemInstruction(),
                s.getCustomInstruction(), s.getTone(), s.getTemperature(), s.getAnswerLength(), s.getTopK(), s.getWelcomeMessage(),
                s.getFallbackMessage(), s.isShowCitations());
        return new ChatbotResponse(bot.getId(), bot.getOrganizationId(), bot.getOwnerId(), bot.getName(), bot.getDescription(), bot.getAvatar(),
                bot.getStatus(), Json.readStrings(bot.getStarterQuestions()), settings, counts.total(), counts.ready(), counts.failed(),
                bot.getCreatedAt(), bot.getUpdatedAt());
    }

    static ChatbotSummary toSummary(Chatbot bot) {
        ChatbotSettings s = bot.getSettings();
        return new ChatbotSummary(bot.getId(), bot.getOrganizationId(), bot.getOwnerId(), bot.getName(), bot.getDescription(), bot.getAvatar(),
                bot.getStatus(), Json.readStrings(bot.getStarterQuestions()),
                new ChatbotSummary.Settings(s.getModelId(), s.getModelName(), s.getPromptTemplateId(), s.getSystemInstruction(), s.getCustomInstruction(),
                        s.getTone(), s.getTemperature(), s.getAnswerLength(), s.getTopK(), s.getWelcomeMessage(), s.getFallbackMessage(), s.isShowCitations()));
    }
}
