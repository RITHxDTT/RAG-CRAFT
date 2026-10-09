package com.ragcraft.chatbot.api;

import com.ragcraft.chatbot.api.ChatbotApi.AdminChatbotResponse;
import com.ragcraft.chatbot.api.ChatbotApi.ChatbotRequest;
import com.ragcraft.chatbot.api.ChatbotApi.ChatbotResponse;
import com.ragcraft.chatbot.api.ChatbotApi.DashboardResponse;
import com.ragcraft.chatbot.service.ChatbotService;
import com.ragcraft.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
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

/** Same routes as the FastAPI chatbots router plus the admin chatbot monitor. */
@RestController
@RequestMapping("/api")
public class ChatbotController {

    private final ChatbotService service;
    private final CurrentUser currentUser;

    public ChatbotController(ChatbotService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() { return service.dashboard(currentUser.require()); }

    @GetMapping("/chatbots")
    public List<ChatbotResponse> list() { return service.list(currentUser.require()); }

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

    @DeleteMapping("/chatbots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id, currentUser.require()); }

    @GetMapping("/admin/chatbots")
    public List<AdminChatbotResponse> adminList(@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "100") int limit) {
        return service.adminList(Math.max(0, offset), limit);
    }
}
