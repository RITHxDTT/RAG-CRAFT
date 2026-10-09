package com.ragcraft.channel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ragcraft.channel.api.ChannelApi.ChannelResponse;
import com.ragcraft.channel.api.ChannelApi.ChannelSettings;
import com.ragcraft.channel.api.ChannelApi.ConnectRequest;
import com.ragcraft.channel.api.ChannelApi.CreateRequest;
import com.ragcraft.channel.api.ChannelApi.GuestAnswer;
import com.ragcraft.channel.api.ChannelApi.GuestAskRequest;
import com.ragcraft.channel.api.ChannelApi.PublicBot;
import com.ragcraft.channel.api.ChannelApi.StatsResponse;
import com.ragcraft.channel.api.ChannelApi.ToggleRequest;
import com.ragcraft.channel.api.ChannelApi.VerifyRequest;
import com.ragcraft.channel.service.ChannelService;
import com.ragcraft.channel.service.PublicChatService;
import com.ragcraft.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChannelController {

    private final ChannelService channels;
    private final PublicChatService publicChat;
    private final CurrentUser currentUser;

    public ChannelController(ChannelService channels, PublicChatService publicChat, CurrentUser currentUser) {
        this.channels = channels;
        this.publicChat = publicChat;
        this.currentUser = currentUser;
    }

    // ----- owner -----
    @GetMapping("/chatbots/{chatbotId}/channels")
    public List<ChannelResponse> list(@PathVariable UUID chatbotId) { return channels.list(chatbotId, currentUser.require()); }

    @PostMapping("/chatbots/{chatbotId}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelResponse create(@PathVariable UUID chatbotId, @Valid @RequestBody CreateRequest request) {
        return channels.create(chatbotId, request.channel(), request.token(), currentUser.require());
    }

    @PutMapping("/chatbots/{chatbotId}/channels/{id}/settings")
    public ChannelResponse settings(@PathVariable UUID chatbotId, @PathVariable UUID id, @RequestBody ChannelSettings settings) {
        return channels.saveSettings(chatbotId, id, settings, currentUser.require());
    }

    @PatchMapping("/chatbots/{chatbotId}/channels/{id}")
    public ChannelResponse toggle(@PathVariable UUID chatbotId, @PathVariable UUID id, @RequestBody(required = false) ToggleRequest request) {
        return channels.toggle(chatbotId, id, request == null ? null : request.enabled(), currentUser.require());
    }

    @PostMapping("/chatbots/{chatbotId}/channels/{id}/regenerate")
    public ChannelResponse regenerate(@PathVariable UUID chatbotId, @PathVariable UUID id) { return channels.regenerate(chatbotId, id, currentUser.require()); }

    @PostMapping("/chatbots/{chatbotId}/channels/{id}/connect")
    public ChannelResponse connect(@PathVariable UUID chatbotId, @PathVariable UUID id, @Valid @RequestBody ConnectRequest request) {
        return channels.connectTelegram(chatbotId, id, request.token(), currentUser.require());
    }

    @PostMapping("/chatbots/{chatbotId}/channels/{id}/test")
    public ChannelResponse test(@PathVariable UUID chatbotId, @PathVariable UUID id) { return channels.testTelegram(chatbotId, id, currentUser.require()); }

    @DeleteMapping("/chatbots/{chatbotId}/channels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID chatbotId, @PathVariable UUID id) { channels.delete(chatbotId, id, currentUser.require()); }

    // ----- public (guests) -----
    @GetMapping("/public/{kind}/{publicId}")
    public PublicBot metadata(@PathVariable String kind, @PathVariable String publicId) { return publicChat.metadata(kind, publicId); }

    @PostMapping("/public/{kind}/{publicId}/verify")
    public Map<String, Boolean> verify(@PathVariable String kind, @PathVariable String publicId, @Valid @RequestBody VerifyRequest request) {
        publicChat.verify(kind, publicId, request.password());
        return Map.of("ok", true);
    }

    @PostMapping("/public/{kind}/{publicId}/ask")
    public GuestAnswer ask(@PathVariable String kind, @PathVariable String publicId, @Valid @RequestBody GuestAskRequest request, HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        String client = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
        return publicChat.ask(kind, publicId, request.question(), request.conversationId(), request.sessionToken(), request.password(), client);
    }

    // ----- Telegram webhook -----
    @PostMapping("/webhooks/telegram/{publicId}")
    public Map<String, Boolean> telegram(@PathVariable String publicId,
                                         @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
                                         @RequestBody JsonNode update) {
        publicChat.telegramUpdate(publicId, secret, update);
        return Map.of("ok", true);
    }

    // ----- internal -----
    @DeleteMapping("/internal/chatbots/{chatbotId}/channels")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForChatbot(@PathVariable UUID chatbotId) { channels.deleteForChatbot(chatbotId); }

    @PostMapping("/internal/channels/counts")
    public Map<UUID, Long> counts(@RequestBody List<UUID> chatbotIds) { return channels.counts(chatbotIds); }

    @GetMapping("/internal/chatbots/{chatbotId}/channels/telegram-status")
    public Map<String, Boolean> telegramStatus(@PathVariable UUID chatbotId) { return Map.of("connected", channels.telegramConnected(chatbotId)); }

    @GetMapping("/internal/stats")
    public StatsResponse stats(@RequestParam(required = false) UUID ownerId) { return channels.stats(ownerId); }
}
