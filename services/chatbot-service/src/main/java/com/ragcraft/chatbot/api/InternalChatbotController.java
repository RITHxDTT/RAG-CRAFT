package com.ragcraft.chatbot.api;

import com.ragcraft.chatbot.api.ChatbotApi.StatsResponse;
import com.ragcraft.chatbot.service.ChatbotService;
import com.ragcraft.common.client.ChatbotSummary;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service endpoints (internal token). Other services check ownership with GET /chatbots/{id}. */
@RestController
@RequestMapping("/api/internal")
public class InternalChatbotController {

    private final ChatbotService service;

    public InternalChatbotController(ChatbotService service) {
        this.service = service;
    }

    @GetMapping("/chatbots/{id}")
    public ChatbotSummary get(@PathVariable UUID id) { return service.summary(id); }

    @GetMapping("/chatbots")
    public List<ChatbotSummary> list(@RequestParam(required = false) UUID ownerId) { return service.summaries(ownerId); }

    @PostMapping("/chatbots/count-by-owner")
    public Map<UUID, Long> countByOwner(@RequestBody List<UUID> ownerIds) { return service.countByOwners(ownerIds); }

    @GetMapping("/stats")
    public StatsResponse stats(@RequestParam(required = false) UUID ownerId) { return service.stats(ownerId); }

    @DeleteMapping("/owners/{ownerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOwner(@PathVariable UUID ownerId) { service.deleteOwner(ownerId); }
}
