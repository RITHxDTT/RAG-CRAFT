package com.ragcraft.chatbot;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragcraft.chatbot.api.ChatbotApi.DocumentCounts;
import com.ragcraft.chatbot.client.CatalogClient;
import com.ragcraft.chatbot.client.IdentityClient;
import com.ragcraft.chatbot.client.RelatedServicesClient;
import com.ragcraft.common.security.JwtService;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.ServiceCallException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** V5 chatbot rules against H2, with the other services mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ChatbotFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @MockitoBean CatalogClient catalog;
    @MockitoBean IdentityClient identity;
    @MockitoBean RelatedServicesClient related;

    UUID owner = UUID.randomUUID();
    final UUID stranger = UUID.randomUUID();
    final UUID admin = UUID.randomUUID();
    final UUID modelId = UUID.randomUUID();
    final UUID fallbackId = UUID.randomUUID();
    final CatalogClient.Model llama = new CatalogClient.Model(modelId, "Llama 3.2 3B", "llama3.2:3b", "LLM", true);
    final CatalogClient.Model qwen = new CatalogClient.Model(fallbackId, "Qwen", "qwen2.5:3b", "LLM", true);
    /** Mutable stand-ins for knowledge-service and channel-service answers. */
    final Map<UUID, DocumentCounts> docs = new HashMap<>();
    final Map<UUID, Long> channels = new HashMap<>();

    @BeforeEach
    void stubs() {
        owner = UUID.randomUUID(); // a fresh owner per test keeps the quota and list assertions independent
        docs.clear();
        channels.clear();
        when(catalog.defaultModel()).thenReturn(llama);
        when(catalog.model(modelId)).thenReturn(llama);
        when(catalog.model(fallbackId)).thenReturn(qwen);
        when(catalog.defaultEmbeddingModel()).thenReturn(new CatalogClient.Model(UUID.randomUUID(), "nomic-embed-text", "nomic-embed-text", "EMBEDDING", true));
        when(catalog.embeddingModels()).thenReturn(List.of(new CatalogClient.Model(UUID.randomUUID(), "nomic-embed-text", "nomic-embed-text", "EMBEDDING", true),
                new CatalogClient.Model(UUID.randomUUID(), "bge-m3", "bge-m3", "EMBEDDING", true)));
        when(catalog.limits()).thenReturn(Map.of(
                "temperature", new CatalogClient.Limit("temperature", BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("0.4")),
                "max_tokens", new CatalogClient.Limit("max_tokens", new BigDecimal(128), new BigDecimal(4096), new BigDecimal(1024)),
                "top_k", new CatalogClient.Limit("top_k", BigDecimal.ONE, new BigDecimal(20), new BigDecimal(5)),
                "max_context_tokens", new CatalogClient.Limit("max_context_tokens", new BigDecimal(512), new BigDecimal(16384), new BigDecimal(4096)),
                "chunk_size", new CatalogClient.Limit("chunk_size", new BigDecimal(100), new BigDecimal(2000), new BigDecimal(500)),
                "chunk_overlap", new CatalogClient.Limit("chunk_overlap", BigDecimal.ZERO, new BigDecimal(500), new BigDecimal(50))));
        when(identity.quota(any())).thenReturn(new IdentityClient.Quota(5, 5L * 1024 * 1024 * 1024));
        when(related.documentCountsOrNull(anyList())).thenAnswer(inv -> docs);
        when(related.documentCounts(anyList())).thenAnswer(inv -> docs);
        when(related.channelCountsOrNull(anyList())).thenAnswer(inv -> channels);
        when(related.channelCounts(anyList())).thenAnswer(inv -> channels);
        when(related.telegramConnected(any())).thenReturn(false);
        when(related.owners(anyList())).thenReturn(Map.of());
    }

    private String bearer(UUID userId, String role) {
        return "Bearer " + jwt.issue(new UserPrincipal(userId, UUID.randomUUID(), role.toLowerCase() + "@example.com", role, 0));
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/chatbots").header("Authorization", bearer(owner, "USER")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createBot(String name) throws Exception {
        String created = create("{\"name\":\"" + name + "\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asText();
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, UUID user, String role) throws Exception {
        return mvc.perform(request.header("Authorization", bearer(user, role)));
    }

    // ------------------------------------------------------------------ creation and settings

    @Test
    void newChatbotsStartAsDraftWithAdminDefaultsAndStayIsolatedPerOwner() throws Exception {
        String created = create("{\"name\":\"HR Assistant\",\"description\":\"Policies\",\"status\":\"ACTIVE\",\"starter_questions\":[\"Leave policy?\"],\"settings\":{\"tone\":\"FRIENDLY\",\"top_k\":4}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.settings.model_name", is("llama3.2:3b")))
                .andExpect(jsonPath("$.settings.embedding_model", is("nomic-embed-text")))
                .andExpect(jsonPath("$.settings.tone", is("FRIENDLY")))
                .andExpect(jsonPath("$.settings.top_k", is(4)))
                .andExpect(jsonPath("$.settings.temperature", is(0.4)))
                .andExpect(jsonPath("$.settings.max_tokens", is(1024)))
                .andExpect(jsonPath("$.settings.chunk_size", is(500)))
                .andExpect(jsonPath("$.settings.search_mode", is("HYBRID")))
                .andExpect(jsonPath("$.settings.answer_length", is("CONCISE")))
                .andExpect(jsonPath("$.settings.answer_from_documents_only", is(true)))
                .andExpect(jsonPath("$.kb_status", is("EMPTY")))
                .andExpect(jsonPath("$.starter_questions", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();
        call(get("/api/chatbots/" + id), stranger, "USER").andExpect(status().isNotFound());
        call(get("/api/chatbots/" + id), stranger, "ADMIN").andExpect(status().isOk());
        call(get("/api/chatbots"), stranger, "USER").andExpect(jsonPath("$", hasSize(0)));
        call(get("/api/internal/chatbots/" + id).header("X-Internal-Token", "change-me-internal-token"), owner, "USER")
                .andExpect(status().isOk()).andExpect(jsonPath("$.owner_id", is(owner.toString()))).andExpect(jsonPath("$.status", is("DRAFT")));
    }

    @Test
    void detailsAndSettingsAreValidated() throws Exception {
        create("{\"name\":\"" + "x".repeat(51) + "\"}").andExpect(status().isUnprocessableEntity());
        create("{\"name\":\"Bot\",\"description\":\"" + "d".repeat(201) + "\"}").andExpect(status().isUnprocessableEntity());
        create("{\"name\":\"Bot\",\"starter_questions\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}").andExpect(status().isUnprocessableEntity());
        create("{\"name\":\"X\",\"settings\":{\"tone\":\"SHOUTY\"}}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Unknown tone.")));
        create("{\"name\":\"X\",\"settings\":{\"tone\":\"CUSTOM\"}}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Describe the custom tone.")));
        create("{\"name\":\"X\",\"settings\":{\"answer_length\":\"LONG\"}}").andExpect(status().isBadRequest());
        create("{\"name\":\"X\",\"settings\":{\"temperature\":1.5}}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Temperature must be between 0 and 1.")));
        create("{\"name\":\"X\",\"settings\":{\"top_k\":21}}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("Top K results")));
        create("{\"name\":\"X\",\"settings\":{\"chunk_size\":200,\"chunk_overlap\":300}}").andExpect(status().isBadRequest());
        create("{\"name\":\"X\",\"settings\":{\"embedding_model\":\"nope\"}}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Choose an available embedding model.")));
        create("{\"name\":\"X\",\"settings\":{\"model_id\":\"" + modelId + "\",\"fallback_model_id\":\"" + modelId + "\"}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("The fallback model must differ from the primary model.")));
        create("{\"name\":\"Bot\",\"settings\":{\"model_id\":\"" + modelId + "\",\"fallback_model_id\":\"" + fallbackId + "\",\"embedding_model\":\"bge-m3\",\"language\":\"km\"}}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.settings.fallback_model_name", is("qwen2.5:3b")))
                .andExpect(jsonPath("$.settings.embedding_model", is("bge-m3"))).andExpect(jsonPath("$.settings.language", is("KM")));
    }

    @Test
    void embeddingModelIsLockedAndTighteningALimitDoesNotBlockUnrelatedEdits() throws Exception {
        String id = createBot("Locked");
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"embedding_model\":\"bge-m3\"}}"), owner, "USER")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("cannot be changed")));
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"embedding_model\":\"nomic-embed-text\",\"top_k\":15}}"), owner, "USER")
                .andExpect(status().isOk()).andExpect(jsonPath("$.settings.top_k", is(15)));
        // The admin lowers the top_k ceiling to 10: the stored 15 is clamped silently when editing something else.
        when(catalog.limits()).thenReturn(Map.of("top_k", new CatalogClient.Limit("top_k", BigDecimal.ONE, BigDecimal.TEN, new BigDecimal(5))));
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"edited\",\"settings\":{\"welcome_message\":\"Hi\"}}"), owner, "USER")
                .andExpect(status().isOk()).andExpect(jsonPath("$.settings.top_k", is(10)));
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"top_k\":15}}"), owner, "USER").andExpect(status().isBadRequest());
    }

    @Test
    void starterQuestionsKeepTheirOrderAcrossUpdates() throws Exception {
        String id = createBot("Starters");
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"starter_questions\":[\"A\",\"B\",\"C\"]}"), owner, "USER")
                .andExpect(jsonPath("$.starter_questions[0]", is("A"))).andExpect(jsonPath("$.starter_questions[2]", is("C")));
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"starter_questions\":[\"C\",\"A\"]}"), owner, "USER").andExpect(status().isOk());
        call(get("/api/chatbots/" + id), owner, "USER").andExpect(jsonPath("$.starter_questions", hasSize(2)))
                .andExpect(jsonPath("$.starter_questions[0]", is("C"))).andExpect(jsonPath("$.starter_questions[1]", is("A")));
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"starter_questions\":[]}"), owner, "USER")
                .andExpect(jsonPath("$.starter_questions", hasSize(0)));
    }

    @Test
    void quotaLimitsHowManyChatbotsAnOwnerCanCreate() throws Exception {
        when(identity.quota(owner)).thenReturn(new IdentityClient.Quota(1, 1024));
        createBot("First");
        create("{\"name\":\"Second\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.code", is("QUOTA_BOTS")))
                .andExpect(jsonPath("$.detail", containsString("Chatbot limit reached (1)")));
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    void lifecycleFollowsDocumentsThenPublishChecklistThenPauseAndResume() throws Exception {
        String id = createBot("Lifecycle");
        UUID botId = UUID.fromString(id);
        // DRAFT cannot be published: nothing is indexed yet.
        call(post("/api/chatbots/" + id + "/publish"), owner, "USER").andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("Add at least one document")));
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"), owner, "USER").andExpect(status().isConflict());

        // A READY document moves DRAFT to PENDING on the next read.
        docs.put(botId, new DocumentCounts(2, 1, 0));
        call(get("/api/chatbots/" + id), owner, "USER").andExpect(jsonPath("$.status", is("PENDING"))).andExpect(jsonPath("$.kb_status", is("PROCESSING")));
        call(get("/api/chatbots/" + id + "/checklist"), owner, "USER").andExpect(jsonPath("$.has_document", is(true))).andExpect(jsonPath("$.channel_ready", is(false))).andExpect(jsonPath("$.ready", is(false)));
        call(post("/api/chatbots/" + id + "/publish"), owner, "USER").andExpect(status().isConflict()).andExpect(jsonPath("$.code", is("CHECKLIST")))
                .andExpect(jsonPath("$.detail", is("Before publishing you need one channel.")));

        channels.put(botId, 1L);
        call(post("/api/chatbots/" + id + "/publish"), owner, "USER").andExpect(status().isOk()).andExpect(jsonPath("$.status", is("ACTIVE")));
        call(post("/api/chatbots/" + id + "/pause"), owner, "USER").andExpect(jsonPath("$.status", is("PAUSED")));
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DRAFT\"}"), owner, "USER").andExpect(status().isConflict());
        call(post("/api/chatbots/" + id + "/resume"), owner, "USER").andExpect(jsonPath("$.status", is("ACTIVE")));

        // Removing the last document sends a PENDING chatbot back to DRAFT, but never touches a live one.
        docs.clear();
        call(get("/api/chatbots/" + id), owner, "USER").andExpect(jsonPath("$.status", is("ACTIVE")));
        call(post("/api/chatbots/" + id + "/pause"), owner, "USER");
        call(post("/api/chatbots/" + id + "/resume"), owner, "USER").andExpect(status().isConflict());

        call(get("/api/chatbots/" + id + "/status-history"), owner, "USER").andExpect(jsonPath("$[?(@.actor_role=='SYSTEM')]").isNotEmpty());
        call(get("/api/chatbots?status=ACTIVE"), owner, "USER").andExpect(jsonPath("$", hasSize(0)));
        call(get("/api/chatbots?status=PAUSED"), owner, "USER").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void anOutageOfTheDocumentServiceIsNotMistakenForAnEmptyKnowledgeBase() throws Exception {
        String id = createBot("Outage");
        UUID botId = UUID.fromString(id);
        docs.put(botId, new DocumentCounts(1, 1, 0));
        call(get("/api/chatbots/" + id), owner, "USER").andExpect(jsonPath("$.status", is("PENDING")));
        when(related.documentCountsOrNull(anyList())).thenReturn(null);
        call(get("/api/chatbots/" + id), owner, "USER").andExpect(jsonPath("$.status", is("PENDING")));
        call(post("/api/chatbots/" + id + "/publish"), owner, "USER").andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code", is("DEPENDENCY_DOWN")));
    }

    @Test
    void deletingNeedsTheChatbotNameRetyped() throws Exception {
        String id = createBot("Delete Me");
        call(delete("/api/chatbots/" + id), owner, "USER").andExpect(status().isBadRequest());
        call(delete("/api/chatbots/" + id + "?confirm_name=wrong"), owner, "USER").andExpect(status().isBadRequest());
        call(delete("/api/chatbots/" + id + "?confirm_name=Delete Me"), stranger, "USER").andExpect(status().isNotFound());
        call(delete("/api/chatbots/" + id + "?confirm_name=Delete Me"), owner, "USER").andExpect(status().isNoContent());
        verify(related).cascadeDelete(UUID.fromString(id));
        call(get("/api/chatbots"), owner, "USER").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void duplicateCopiesSettingsAsADraftAndRespectsTheQuota() throws Exception {
        String id = createBot("Original");
        call(post("/api/chatbots/" + id + "/duplicate"), owner, "USER").andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Original (copy)"))).andExpect(jsonPath("$.status", is("DRAFT"))).andExpect(jsonPath("$.settings.embedding_model", is("nomic-embed-text")));
        when(identity.quota(owner)).thenReturn(new IdentityClient.Quota(2, 1024));
        call(post("/api/chatbots/" + id + "/duplicate"), owner, "USER").andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ moderation and appeals

    @Test
    void adminDisableAppealRejectApproveAndReenableAreAuditedAndGuardTheOwner() throws Exception {
        String id = createBot("Moderated");
        UUID botId = UUID.fromString(id);
        docs.put(botId, new DocumentCounts(1, 1, 0));
        channels.put(botId, 1L);
        call(post("/api/chatbots/" + id + "/publish"), owner, "USER").andExpect(status().isOk());

        // Only admins disable, and a reason is mandatory.
        call(post("/api/admin/chatbots/" + id + "/disable").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Spam\"}"), owner, "USER").andExpect(status().isForbidden());
        call(post("/api/admin/chatbots/" + id + "/disable").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"), admin, "ADMIN").andExpect(status().is4xxClientError());
        call(post("/api/admin/chatbots/" + id + "/disable").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Spam content\"}"), admin, "ADMIN").andExpect(status().isOk());
        verify(identity).audit(any(), eq("FORCE_DISABLE_CHATBOT"), eq("CHATBOT"), eq(botId), eq("Moderated"), eq("Spam content"), any());
        call(post("/api/admin/chatbots/" + id + "/disable").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"), admin, "ADMIN").andExpect(status().isConflict());

        // The owner sees the reason and cannot change the status or publish; guests are blocked by the channel services via status.
        call(get("/api/chatbots/" + id), owner, "USER").andExpect(jsonPath("$.status", is("DISABLED"))).andExpect(jsonPath("$.disabled_reason", is("Spam content")));
        call(post("/api/chatbots/" + id + "/resume"), owner, "USER").andExpect(status().isForbidden()).andExpect(jsonPath("$.code", is("DISABLED")));
        call(patch("/api/chatbots/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"), owner, "USER").andExpect(status().isForbidden());

        // Appeals: owner only, message required (max 500), one pending at a time.
        call(post("/api/chatbots/" + id + "/appeals").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Mistake\"}"), stranger, "USER").andExpect(status().isForbidden());
        call(post("/api/chatbots/" + id + "/appeals").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"), owner, "USER").andExpect(status().isUnprocessableEntity());
        call(post("/api/chatbots/" + id + "/appeals").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + "x".repeat(501) + "\"}"), owner, "USER").andExpect(status().isUnprocessableEntity());
        String appeal = call(post("/api/chatbots/" + id + "/appeals").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"It was a mistake.\"}"), owner, "USER")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status", is("PENDING"))).andReturn().getResponse().getContentAsString();
        String appealId = json.readTree(appeal).get("id").asText();
        call(post("/api/chatbots/" + id + "/appeals").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"again\"}"), owner, "USER").andExpect(status().isConflict());
        call(get("/api/admin/appeals/pending-count"), admin, "ADMIN").andExpect(jsonPath("$.count", is(1)));
        call(get("/api/admin/appeals?status=PENDING"), owner, "USER").andExpect(status().isForbidden());

        // Rejecting needs a reason and is audited; afterwards a new appeal is allowed.
        call(post("/api/admin/appeals/" + appealId + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"), admin, "ADMIN").andExpect(status().is4xxClientError());
        verify(identity, never()).audit(any(), eq("REJECT_APPEAL"), any(), any(), any(), any(), any());
        call(post("/api/admin/appeals/" + appealId + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Still violates policy\"}"), admin, "ADMIN")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("REJECTED"))).andExpect(jsonPath("$.decision_reason", is("Still violates policy")));
        call(post("/api/admin/appeals/" + appealId + "/approve"), admin, "ADMIN").andExpect(status().isConflict());
        call(get("/api/chatbots/" + id + "/appeals"), owner, "USER").andExpect(jsonPath("$[0].decision_reason", is("Still violates policy")));
        String second = call(post("/api/chatbots/" + id + "/appeals").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Content removed.\"}"), owner, "USER")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        // Approving returns the chatbot as PAUSED and clears the disable reason.
        call(post("/api/admin/appeals/" + json.readTree(second).get("id").asText() + "/approve").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Looks fine now\"}"), admin, "ADMIN")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("APPROVED")));
        verify(identity).audit(any(), eq("APPROVE_APPEAL"), eq("APPEAL"), any(), eq("Moderated"), eq("Looks fine now"), any());
        call(get("/api/chatbots/" + id), owner, "USER").andExpect(jsonPath("$.status", is("PAUSED"))).andExpect(jsonPath("$.disabled_reason").doesNotExist());
        call(post("/api/chatbots/" + id + "/resume"), owner, "USER").andExpect(jsonPath("$.status", is("ACTIVE")));

        // Direct re-enable closes a pending appeal too.
        call(post("/api/admin/chatbots/" + id + "/disable").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Second strike\"}"), admin, "ADMIN").andExpect(status().isOk());
        call(post("/api/chatbots/" + id + "/appeals").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Please\"}"), owner, "USER").andExpect(status().isCreated());
        call(post("/api/admin/chatbots/" + id + "/reenable").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reviewed offline\"}"), admin, "ADMIN").andExpect(status().isOk());
        verify(identity, atLeastOnce()).audit(any(), eq("REENABLE_CHATBOT"), any(), any(), any(), eq("Reviewed offline"), any());
        call(get("/api/admin/appeals/pending-count"), admin, "ADMIN").andExpect(jsonPath("$.count", is(0)));
        call(get("/api/chatbots/" + id + "/status-history"), owner, "USER").andExpect(jsonPath("$[?(@.actor_role=='ADMIN')]").isNotEmpty());
    }

    @Test
    void aDisableIsNeverAppliedWhenTheAuditLogCannotBeWritten() throws Exception {
        String id = createBot("Unaudited");
        doThrow(new ServiceCallException(HttpStatus.SERVICE_UNAVAILABLE, "identity down")).when(identity).audit(any(), any(), any(), any(), any(), any(), any());
        call(post("/api/admin/chatbots/" + id + "/disable").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Spam\"}"), admin, "ADMIN").andExpect(status().isServiceUnavailable());
        call(get("/api/chatbots/" + id), owner, "USER").andExpect(jsonPath("$.status", is("DRAFT"))).andExpect(jsonPath("$.disabled_reason").doesNotExist());
    }
}
