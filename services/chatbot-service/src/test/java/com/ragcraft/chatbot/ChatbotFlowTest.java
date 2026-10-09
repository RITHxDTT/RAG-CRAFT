package com.ragcraft.chatbot;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragcraft.chatbot.client.CatalogClient;
import com.ragcraft.chatbot.client.RelatedServicesClient;
import com.ragcraft.common.security.JwtService;
import com.ragcraft.common.security.UserPrincipal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ChatbotFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @MockitoBean CatalogClient catalog;
    @MockitoBean RelatedServicesClient related;

    final UUID owner = UUID.randomUUID();
    final UUID stranger = UUID.randomUUID();
    final UUID modelId = UUID.randomUUID();

    @BeforeEach
    void stubs() {
        when(catalog.defaultModel()).thenReturn(new CatalogClient.Model(modelId, "Llama 3.2 3B", "llama3.2:3b", true));
        when(catalog.model(any())).thenReturn(new CatalogClient.Model(modelId, "Llama 3.2 3B", "llama3.2:3b", true));
        when(related.documentCounts(anyList())).thenReturn(Map.of());
        when(related.telegramConnected(any())).thenReturn(false);
    }

    private String bearer(UUID userId, String role) {
        return "Bearer " + jwt.issue(new UserPrincipal(userId, UUID.randomUUID(), "u@example.com", role, 0));
    }

    @Test
    void ownerCanManageChatbotAndOthersCannotSeeIt() throws Exception {
        String created = mvc.perform(post("/api/chatbots").header("Authorization", bearer(owner, "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HR Assistant\",\"description\":\"Policies\",\"starter_questions\":[\"Leave policy?\"],\"settings\":{\"tone\":\"FRIENDLY\",\"top_k\":4}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.settings.model_name", is("llama3.2:3b")))
                .andExpect(jsonPath("$.settings.tone", is("FRIENDLY")))
                .andExpect(jsonPath("$.starter_questions", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();

        mvc.perform(get("/api/chatbots/" + id).header("Authorization", bearer(stranger, "USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/chatbots/" + id).header("Authorization", bearer(stranger, "ADMIN"))).andExpect(status().isOk());

        mvc.perform(patch("/api/chatbots/" + id).header("Authorization", bearer(owner, "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        mvc.perform(get("/api/dashboard").header("Authorization", bearer(owner, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_chatbots", is(1)))
                .andExpect(jsonPath("$.active_chatbots", is(1)));

        mvc.perform(get("/api/internal/chatbots/" + id).header("X-Internal-Token", "change-me-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_id", is(owner.toString())));

        mvc.perform(post("/api/chatbots/" + id + "/duplicate").header("Authorization", bearer(owner, "USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("HR Assistant (copy)")));

        mvc.perform(delete("/api/chatbots/" + id).header("Authorization", bearer(owner, "USER"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/chatbots").header("Authorization", bearer(owner, "USER"))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void invalidToneIsRejected() throws Exception {
        mvc.perform(post("/api/chatbots").header("Authorization", bearer(owner, "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"settings\":{\"tone\":\"SHOUTY\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Unknown tone.")));
    }
}
