package com.ragcraft.chatbot.service;

import com.ragcraft.chatbot.api.ChatbotApi.AppealResponse;
import com.ragcraft.chatbot.client.IdentityClient;
import com.ragcraft.chatbot.client.RelatedServicesClient;
import com.ragcraft.chatbot.domain.Chatbot;
import com.ragcraft.chatbot.domain.ChatbotAppeal;
import com.ragcraft.chatbot.domain.ChatbotStatusHistory;
import com.ragcraft.chatbot.repository.ChatbotAppealRepository;
import com.ragcraft.chatbot.repository.ChatbotRepository;
import com.ragcraft.chatbot.repository.ChatbotStatusHistoryRepository;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin moderation of chatbots and the owner's appeal flow. Every admin decision is written to identity-service's audit log
 * BEFORE the change is applied, so an action can never happen without a recorded reason.
 */
@Service
public class ModerationService {

    private final ChatbotRepository chatbots;
    private final ChatbotAppealRepository appeals;
    private final ChatbotStatusHistoryRepository history;
    private final IdentityClient identity;
    private final RelatedServicesClient related;

    public ModerationService(ChatbotRepository chatbots, ChatbotAppealRepository appeals, ChatbotStatusHistoryRepository history,
                             IdentityClient identity, RelatedServicesClient related) {
        this.chatbots = chatbots;
        this.appeals = appeals;
        this.history = history;
        this.identity = identity;
        this.related = related;
    }

    /** Disables a chatbot on every guest channel. The owner sees the reason and may appeal. */
    @Transactional
    public void disable(UserPrincipal admin, UUID chatbotId, String reason) {
        Chatbot bot = bot(chatbotId);
        String why = requireReason(reason);
        if (Chatbot.DISABLED.equals(bot.getStatus())) throw AppException.conflict("This chatbot is already disabled.");
        identity.audit(admin, "FORCE_DISABLE_CHATBOT", "CHATBOT", bot.getId(), bot.getName(), why, Map.of("previous_status", bot.getStatus()));
        history.save(ChatbotStatusHistory.of(bot.getId(), bot.getStatus(), Chatbot.DISABLED, admin.userId(), "ADMIN", why));
        bot.setStatus(Chatbot.DISABLED);
        bot.setDisabledReason(why);
        bot.setDisabledAt(Instant.now());
        bot.setDisabledBy(admin.userId());
        chatbots.save(bot);
    }

    /** Lifts a disable without an appeal. The chatbot comes back PAUSED and the owner resumes it. */
    @Transactional
    public void reenable(UserPrincipal admin, UUID chatbotId, String reason) {
        Chatbot bot = bot(chatbotId);
        String why = requireReason(reason);
        if (!Chatbot.DISABLED.equals(bot.getStatus())) throw AppException.conflict("Only disabled chatbots can be re-enabled.");
        identity.audit(admin, "REENABLE_CHATBOT", "CHATBOT", bot.getId(), bot.getName(), why, Map.of());
        resume(bot, admin, why);
        appeals.findFirstByChatbotIdAndStatus(bot.getId(), ChatbotAppeal.PENDING).ifPresent(open -> decide(open, ChatbotAppeal.APPROVED, admin, "The chatbot was re-enabled by an administrator."));
    }

    // ------------------------------------------------------------------ appeals (owner)

    /** One PENDING appeal per DISABLED chatbot; the message is required and limited to 500 characters. */
    @Transactional
    public AppealResponse submit(UserPrincipal owner, UUID chatbotId, String message) {
        Chatbot bot = bot(chatbotId);
        if (!bot.getOwnerId().equals(owner.userId())) throw AppException.forbidden("Only the chatbot owner can submit an appeal.");
        if (!Chatbot.DISABLED.equals(bot.getStatus())) throw AppException.conflict("Only disabled chatbots can be appealed.");
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) throw AppException.badRequest("Describe why the chatbot should be re-enabled.");
        if (text.length() > 500) throw AppException.badRequest("Appeal messages can be up to 500 characters.");
        if (appeals.findFirstByChatbotIdAndStatus(chatbotId, ChatbotAppeal.PENDING).isPresent()) {
            throw AppException.conflict("You already have a pending appeal for this chatbot.");
        }
        ChatbotAppeal appeal = new ChatbotAppeal();
        appeal.setChatbotId(chatbotId);
        appeal.setOwnerId(owner.userId());
        appeal.setMessage(text);
        return toResponses(List.of(appeals.save(appeal))).get(0);
    }

    @Transactional(readOnly = true)
    public List<AppealResponse> forChatbot(UserPrincipal principal, UUID chatbotId) {
        Chatbot bot = bot(chatbotId);
        if (!principal.canAccess(bot.getOwnerId())) throw AppException.notFound("Chatbot not found.");
        return toResponses(appeals.findByChatbotIdOrderByCreatedAtDesc(chatbotId));
    }

    // ------------------------------------------------------------------ appeals (admin)

    @Transactional(readOnly = true)
    public List<AppealResponse> list(String status) {
        return toResponses(status == null || status.isBlank() ? appeals.findAllByOrderByCreatedAtDesc() : appeals.findByStatusOrderByCreatedAtDesc(status.toUpperCase()));
    }

    @Transactional(readOnly = true)
    public long pendingCount() { return appeals.countByStatus(ChatbotAppeal.PENDING); }

    /** Approving returns the chatbot as PAUSED (the owner resumes it). */
    @Transactional
    public AppealResponse approve(UserPrincipal admin, UUID appealId, String reason) {
        ChatbotAppeal appeal = pending(appealId);
        Chatbot bot = bot(appeal.getChatbotId());
        String why = reason == null || reason.isBlank() ? "Appeal approved." : reason.trim();
        identity.audit(admin, "APPROVE_APPEAL", "APPEAL", appeal.getId(), bot.getName(), why, Map.of("chatbot_id", bot.getId().toString()));
        if (Chatbot.DISABLED.equals(bot.getStatus())) resume(bot, admin, "Appeal approved: " + why);
        return toResponses(List.of(decide(appeal, ChatbotAppeal.APPROVED, admin, why))).get(0);
    }

    @Transactional
    public AppealResponse reject(UserPrincipal admin, UUID appealId, String reason) {
        ChatbotAppeal appeal = pending(appealId);
        String why = requireReason(reason);
        Chatbot bot = bot(appeal.getChatbotId());
        identity.audit(admin, "REJECT_APPEAL", "APPEAL", appeal.getId(), bot.getName(), why, Map.of("chatbot_id", bot.getId().toString()));
        return toResponses(List.of(decide(appeal, ChatbotAppeal.REJECTED, admin, why))).get(0);
    }

    // ------------------------------------------------------------------ helpers

    private void resume(Chatbot bot, UserPrincipal admin, String reason) {
        history.save(ChatbotStatusHistory.of(bot.getId(), Chatbot.DISABLED, Chatbot.PAUSED, admin.userId(), "ADMIN", reason));
        bot.setStatus(Chatbot.PAUSED);
        bot.setDisabledReason(null);
        bot.setDisabledAt(null);
        bot.setDisabledBy(null);
        chatbots.save(bot);
    }

    private ChatbotAppeal decide(ChatbotAppeal appeal, String status, UserPrincipal admin, String reason) {
        appeal.setStatus(status);
        appeal.setDecisionReason(reason);
        appeal.setDecidedBy(admin.userId());
        appeal.setDecidedAt(Instant.now());
        return appeals.save(appeal);
    }

    private ChatbotAppeal pending(UUID id) {
        ChatbotAppeal appeal = appeals.findById(id).orElseThrow(() -> AppException.notFound("Appeal not found."));
        if (!ChatbotAppeal.PENDING.equals(appeal.getStatus())) throw AppException.conflict("This appeal has already been decided.");
        return appeal;
    }

    private Chatbot bot(UUID id) {
        return chatbots.findById(id).orElseThrow(() -> AppException.notFound("Chatbot not found."));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw AppException.withCode(HttpStatus.BAD_REQUEST, "A reason is required.", "REASON_REQUIRED");
        return reason.trim();
    }

    private List<AppealResponse> toResponses(List<ChatbotAppeal> rows) {
        Map<UUID, Chatbot> bots = new java.util.HashMap<>();
        chatbots.findAllById(rows.stream().map(ChatbotAppeal::getChatbotId).distinct().toList()).forEach(b -> bots.put(b.getId(), b));
        Map<UUID, RelatedServicesClient.OwnerSummary> owners = related.owners(rows.stream().map(ChatbotAppeal::getOwnerId).distinct().toList());
        return rows.stream().map(a -> new AppealResponse(a.getId(), a.getChatbotId(), bots.containsKey(a.getChatbotId()) ? bots.get(a.getChatbotId()).getName() : null,
                a.getOwnerId(), owners.containsKey(a.getOwnerId()) ? owners.get(a.getOwnerId()).email() : null, a.getMessage(), a.getStatus(),
                a.getDecisionReason(), a.getCreatedAt(), a.getDecidedAt())).toList();
    }
}
