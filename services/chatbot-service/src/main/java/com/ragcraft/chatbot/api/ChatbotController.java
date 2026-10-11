package com.ragcraft.chatbot.api;

import com.ragcraft.chatbot.api.ChatbotApi.AdminChatbotResponse;
import com.ragcraft.chatbot.api.ChatbotApi.AppealRequest;
import com.ragcraft.chatbot.api.ChatbotApi.AppealResponse;
import com.ragcraft.chatbot.api.ChatbotApi.ChatbotRequest;
import com.ragcraft.chatbot.api.ChatbotApi.ChatbotResponse;
import com.ragcraft.chatbot.api.ChatbotApi.DashboardResponse;
import com.ragcraft.chatbot.api.ChatbotApi.DecisionRequest;
import com.ragcraft.chatbot.api.ChatbotApi.HistoryResponse;
import com.ragcraft.chatbot.api.ChatbotApi.PublishChecklist;
import com.ragcraft.chatbot.api.ChatbotApi.ReasonRequest;
import com.ragcraft.chatbot.service.ChatbotService;
import com.ragcraft.chatbot.service.ModerationService;
import com.ragcraft.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Same routes as the FastAPI chatbots router plus lifecycle, appeals and the admin moderation actions. */
@RestController
@RequestMapping("/api")
public class ChatbotController {

    private final ChatbotService service;
    private final ModerationService moderation;
    private final CurrentUser currentUser;

    public ChatbotController(ChatbotService service, ModerationService moderation, CurrentUser currentUser) {
        this.service = service;
        this.moderation = moderation;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() { return service.dashboard(currentUser.require()); }

    /** Search by bot name, filter by status, sort by UPDATED, NAME_ASC or NAME_DESC. */
    @GetMapping("/chatbots")
    public List<ChatbotResponse> list(@RequestParam(required = false) String search, @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String sort) {
        return service.list(currentUser.require(), new ChatbotService.Query(search, status, sort));
    }

    @PostMapping("/chatbots")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatbotResponse create(@Valid @RequestBody ChatbotRequest request) { return service.create(request, currentUser.require()); }

    @GetMapping("/chatbots/{id}")
    public ChatbotResponse get(@PathVariable UUID id) { return service.get(id, currentUser.require()); }

    @PatchMapping("/chatbots/{id}")
    public ChatbotResponse update(@PathVariable UUID id, @Valid @RequestBody ChatbotRequest request) {
        return service.update(id, request, currentUser.require());
    }

    @PostMapping("/chatbots/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatbotResponse duplicate(@PathVariable UUID id) { return service.duplicate(id, currentUser.require()); }

    /** The chatbot name must be sent as confirm_name. */
    @DeleteMapping("/chatbots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam(name = "confirm_name", required = false) String confirmName) {
        service.delete(id, confirmName, currentUser.require());
    }

    // ----- lifecycle -----
    @GetMapping("/chatbots/{id}/checklist")
    public PublishChecklist checklist(@PathVariable UUID id) { return service.checklist(id, currentUser.require()); }

    @PostMapping("/chatbots/{id}/publish")
    public ChatbotResponse publish(@PathVariable UUID id) { return service.publish(id, currentUser.require()); }

    @PostMapping("/chatbots/{id}/pause")
    public ChatbotResponse pause(@PathVariable UUID id) { return service.pause(id, currentUser.require()); }

    @PostMapping("/chatbots/{id}/resume")
    public ChatbotResponse resume(@PathVariable UUID id) { return service.resume(id, currentUser.require()); }

    @GetMapping("/chatbots/{id}/status-history")
    public List<HistoryResponse> statusHistory(@PathVariable UUID id) { return service.history(id, currentUser.require()); }

    // ----- appeals (owner) -----
    @PostMapping("/chatbots/{id}/appeals")
    @ResponseStatus(HttpStatus.CREATED)
    public AppealResponse appeal(@PathVariable UUID id, @Valid @RequestBody AppealRequest request) {
        return moderation.submit(currentUser.require(), id, request.message());
    }

    @GetMapping("/chatbots/{id}/appeals")
    public List<AppealResponse> appeals(@PathVariable UUID id) { return moderation.forChatbot(currentUser.require(), id); }

    // ----- admin -----
    @GetMapping("/admin/chatbots")
    public List<AdminChatbotResponse> adminList(@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "100") int limit) {
        return service.adminList(Math.max(0, offset), limit);
    }

    @PostMapping("/admin/chatbots/{id}/disable")
    public Map<String, String> disable(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        moderation.disable(currentUser.requireAdmin(), id, request.reason());
        return Map.of("detail", "Chatbot disabled.");
    }

    @PostMapping("/admin/chatbots/{id}/reenable")
    public Map<String, String> reenable(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        moderation.reenable(currentUser.requireAdmin(), id, request.reason());
        return Map.of("detail", "Chatbot re-enabled and paused.");
    }

    @GetMapping("/admin/appeals")
    public List<AppealResponse> adminAppeals(@RequestParam(required = false) String status) {
        currentUser.requireAdmin();
        return moderation.list(status);
    }

    @GetMapping("/admin/appeals/pending-count")
    public Map<String, Long> pendingAppeals() {
        currentUser.requireAdmin();
        return Map.of("count", moderation.pendingCount());
    }

    @PostMapping("/admin/appeals/{appealId}/approve")
    public AppealResponse approve(@PathVariable UUID appealId, @RequestBody(required = false) DecisionRequest request) {
        return moderation.approve(currentUser.requireAdmin(), appealId, request == null ? null : request.reason());
    }

    @PostMapping("/admin/appeals/{appealId}/reject")
    public AppealResponse reject(@PathVariable UUID appealId, @Valid @RequestBody ReasonRequest request) {
        return moderation.reject(currentUser.requireAdmin(), appealId, request.reason());
    }
}
