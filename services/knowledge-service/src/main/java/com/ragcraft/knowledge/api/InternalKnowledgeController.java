package com.ragcraft.knowledge.api;

import com.ragcraft.knowledge.api.KnowledgeApi.DocumentCounts;
import com.ragcraft.knowledge.api.KnowledgeApi.ReadyDocument;
import com.ragcraft.knowledge.api.KnowledgeApi.StatsResponse;
import com.ragcraft.knowledge.service.KnowledgeService;
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

@RestController
@RequestMapping("/api/internal")
public class InternalKnowledgeController {

    private final KnowledgeService service;

    public InternalKnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    /** chatbot-service: document counts for lists and dashboards. */
    @PostMapping("/documents/counts")
    public Map<UUID, DocumentCounts> counts(@RequestBody List<UUID> chatbotIds) { return service.counts(chatbotIds); }

    /** conversation-service: READY documents with sample chunks for citations. */
    @GetMapping("/chatbots/{chatbotId}/documents")
    public List<ReadyDocument> ready(@PathVariable UUID chatbotId, @RequestParam(defaultValue = "2") int chunkLimit) {
        return service.readyDocuments(chatbotId, chunkLimit);
    }

    /** chatbot-service: cascade when a chatbot is deleted. */
    @DeleteMapping("/chatbots/{chatbotId}/documents")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForChatbot(@PathVariable UUID chatbotId) { service.deleteForChatbot(chatbotId); }

    /** analytics-service: knowledge counts, optionally for one owner. */
    @GetMapping("/stats")
    public StatsResponse stats(@RequestParam(required = false) UUID ownerId) { return service.stats(ownerId); }
}
