package com.ragcraft.knowledge;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragcraft.common.client.ChatbotAccess;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.security.JwtService;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import com.ragcraft.knowledge.domain.Document;
import com.ragcraft.knowledge.repository.DocumentRepository;
import com.ragcraft.knowledge.service.MockIngestionProcessor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class KnowledgeFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @Autowired DocumentRepository documents;
    @Autowired MockIngestionProcessor processor;
    @Autowired com.ragcraft.knowledge.repository.KnowledgeSourceRepository sourceRepository;
    @MockitoBean ChatbotAccess chatbots;
    @MockitoBean com.ragcraft.knowledge.service.QuotaGuard quota;

    final UUID owner = UUID.randomUUID();
    final UUID stranger = UUID.randomUUID();
    final UUID chatbot = UUID.randomUUID();

    @BeforeEach
    void stubs() {
        ChatbotSummary bot = new ChatbotSummary(chatbot, UUID.randomUUID(), owner, "Company Assistant", "", null, "ACTIVE", List.of(), null);
        when(chatbots.fetch(chatbot)).thenReturn(bot);
        when(chatbots.require(eq(chatbot), any())).thenAnswer(invocation -> {
            UserPrincipal principal = invocation.getArgument(1);
            if (!principal.canAccess(owner)) throw AppException.notFound("Chatbot not found.");
            return bot;
        });
    }

    private String bearer(UUID userId) {
        return "Bearer " + jwt.issue(new UserPrincipal(userId, UUID.randomUUID(), "u@example.com", "USER", 0));
    }

    @Test
    void uploadIsProcessedIntoChunksAndScopedToTheOwner() throws Exception {
        String text = "Employees receive 18 days of annual leave.\n\nRemote work is allowed three days per week.\n\n" + "Lorem ipsum dolor sit amet. ".repeat(80);
        MockMultipartFile file = new MockMultipartFile("file", "handbook.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8));

        String created = mvc.perform(multipart("/api/chatbots/" + chatbot + "/documents").file(file).header("Authorization", bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.file_type", is("TXT")))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(created).get("id").asText());
        // The hosted schema only accepts DOCUMENT or WEBSITE here, so a drifting constant would fail every upload there.
        org.junit.jupiter.api.Assertions.assertTrue(sourceRepository.findAll().stream().allMatch(source -> java.util.Set.of("DOCUMENT", "WEBSITE").contains(source.getSourceType())));

        // Duplicate content is rejected for the same chatbot.
        mvc.perform(multipart("/api/chatbots/" + chatbot + "/documents").file(file).header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());

        // Fast-forward the simulated pipeline instead of waiting for the scheduler.
        processor.completeNow(documents.findById(id).orElseThrow());

        mvc.perform(get("/api/chatbots/" + chatbot + "/documents/" + id).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.chunk_count", greaterThan(1)))
                .andExpect(jsonPath("$.jobs[0].status", is("QUEUED")));

        mvc.perform(get("/api/chatbots/" + chatbot + "/documents/" + id + "/preview").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks[0].content").isString());

        mvc.perform(get("/api/chatbots/" + chatbot + "/documents").header("Authorization", bearer(stranger))).andExpect(status().isNotFound());

        mvc.perform(get("/api/internal/chatbots/" + chatbot + "/documents").header("X-Internal-Token", "change-me-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].chunks[0].content").isString());

        mvc.perform(post("/api/internal/documents/counts").header("X-Internal-Token", "change-me-internal-token")
                        .contentType("application/json").content("[\"" + chatbot + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + chatbot + "'].ready", is(1)));

        mvc.perform(delete("/api/chatbots/" + chatbot + "/documents/" + id).header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/chatbots/" + chatbot + "/documents").header("Authorization", bearer(owner))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void websiteSourceAndValidation() throws Exception {
        mvc.perform(post("/api/chatbots/" + chatbot + "/documents/website").header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"name\":\"Docs\",\"url\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatbots/" + chatbot + "/documents/website").header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"name\":\"Docs\",\"url\":\"https://example.com/help\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_type", is("WEBSITE")))
                .andExpect(jsonPath("$.url", is("https://example.com/help")));
        MockMultipartFile bad = new MockMultipartFile("file", "evil.exe", "application/octet-stream", new byte[] {1, 2, 3});
        mvc.perform(multipart("/api/chatbots/" + chatbot + "/documents").file(bad).header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Unsupported file type.")));
        Document pending = documents.findByChatbotIdOrderByCreatedAtDesc(chatbot).get(0);
        processor.completeNow(pending);
        mvc.perform(get("/api/knowledge/recent").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chatbot_name", is("Company Assistant")));
    }

    @Test
    void sameNameNeedsReplaceOrSkipAndStorageQuotaIsEnforced() throws Exception {
        MockMultipartFile first = new MockMultipartFile("file", "Policy.txt", "text/plain", "Leave policy: eighteen days.".getBytes(StandardCharsets.UTF_8));
        String created = mvc.perform(multipart("/api/chatbots/" + chatbot + "/documents").file(first).header("Authorization", bearer(owner)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(created).get("id").asText());

        // Different content but the same name (case-insensitive): the caller must choose Replace or Skip.
        MockMultipartFile sameName = new MockMultipartFile("file", "policy.TXT", "text/plain", "A newer policy text.".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/chatbots/" + chatbot + "/documents").file(sameName).header("Authorization", bearer(owner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", is("DUPLICATE_NAME")));
        // Replace keeps the document and queues it again.
        mvc.perform(multipart("/api/chatbots/" + chatbot + "/documents/" + id + "/replace").file(sameName).with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("QUEUED")));

        // The storage quota is checked before anything is stored.
        org.mockito.Mockito.doThrow(AppException.withCode(org.springframework.http.HttpStatus.FORBIDDEN, "Storage limit reached.", "QUOTA_STORAGE"))
                .when(quota).assertCanStore(any(), org.mockito.ArgumentMatchers.anyLong());
        MockMultipartFile other = new MockMultipartFile("file", "other.txt", "text/plain", "Something else entirely.".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/chatbots/" + chatbot + "/documents").file(other).header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code", is("QUOTA_STORAGE")));
        mvc.perform(get("/api/chatbots/" + chatbot + "/documents").header("Authorization", bearer(owner))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/internal/owners/" + owner + "/storage").header("X-Internal-Token", "change-me-internal-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bytes_used", greaterThan(0)));
    }
}
