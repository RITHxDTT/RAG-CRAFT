package com.ragcraft.conversation.api;

import com.ragcraft.common.security.CurrentUser;
import com.ragcraft.conversation.api.ConversationApi.AnswerResponse;
import com.ragcraft.conversation.api.ConversationApi.AskRequest;
import com.ragcraft.conversation.api.ConversationApi.CompareRequest;
import com.ragcraft.conversation.api.ConversationApi.ConversationDetailResponse;
import com.ragcraft.conversation.api.ConversationApi.ConversationResponse;
import com.ragcraft.conversation.api.ConversationApi.InternalAskRequest;
import com.ragcraft.conversation.api.ConversationApi.StatsResponse;
import com.ragcraft.conversation.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
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

/** Same paths as the FastAPI playground router, plus compare mode and the internal ask used by channels. */
@RestController
@RequestMapping("/api")
public class ConversationController {

    private final ConversationService service;
    private final CurrentUser currentUser;

    public ConversationController(ConversationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/chatbots/{chatbotId}/ask")
    public AnswerResponse ask(@PathVariable UUID chatbotId, @Valid @RequestBody AskRequest request) {
        return service.ask(chatbotId, request.question(), request.conversationId(), request.model(), currentUser.require());
    }

    @PostMapping("/chatbots/{chatbotId}/compare")
    public List<AnswerResponse> compare(@PathVariable UUID chatbotId, @Valid @RequestBody CompareRequest request) {
        return service.compare(chatbotId, request.question(), request.models(), currentUser.require());
    }

    @GetMapping("/chatbots/{chatbotId}/conversations")
    public List<ConversationResponse> list(@PathVariable UUID chatbotId) { return service.list(chatbotId, currentUser.require()); }

    @GetMapping("/chatbots/{chatbotId}/conversations/{conversationId}")
    public ConversationDetailResponse get(@PathVariable UUID chatbotId, @PathVariable UUID conversationId) {
        return service.get(chatbotId, conversationId, currentUser.require());
    }

    @DeleteMapping("/chatbots/{chatbotId}/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID chatbotId, @PathVariable UUID conversationId) {
        service.delete(chatbotId, conversationId, currentUser.require());
    }

    // ----- internal -----

    @PostMapping("/internal/ask")
    public AnswerResponse internalAsk(@Valid @RequestBody InternalAskRequest request) { return service.internalAsk(request); }

    @DeleteMapping("/internal/chatbots/{chatbotId}/conversations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForChatbot(@PathVariable UUID chatbotId) { service.deleteForChatbot(chatbotId); }

    @GetMapping("/internal/stats")
    public StatsResponse stats(@RequestParam(required = false) UUID ownerId) { return service.stats(ownerId); }
}
