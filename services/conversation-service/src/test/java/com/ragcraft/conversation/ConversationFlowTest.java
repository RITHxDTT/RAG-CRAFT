package com.ragcraft.conversation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragcraft.common.client.ChatbotAccess;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.security.JwtService;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import com.ragcraft.conversation.client.AnalyticsClient;
import com.ragcraft.conversation.client.KnowledgeClient;
import java.util.List;
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
class ConversationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;
    @MockitoBean ChatbotAccess chatbots;
    @MockitoBean KnowledgeClient knowledge;
    @MockitoBean AnalyticsClient analytics;

    final UUID owner = UUID.randomUUID();
    final UUID stranger = UUID.randomUUID();
    final UUID chatbot = UUID.randomUUID();
    final UUID document = UUID.randomUUID();

    @BeforeEach
    void stubs() {
        ChatbotSummary.Settings settings = new ChatbotSummary.Settings(null, "llama3.2:3b", null, "Answer using the provided knowledge.", null,
                "FRIENDLY", 0.2, "MEDIUM", 5, "Hello!", "Nothing in the knowledge base.", true);
        ChatbotSummary bot = new ChatbotSummary(chatbot, UUID.randomUUID(), owner, "Company Assistant", "", null, "ACTIVE", List.of(), settings);
        when(chatbots.fetch(chatbot)).thenReturn(bot);
        when(chatbots.require(eq(chatbot), any())).thenAnswer(invocation -> {
            UserPrincipal principal = invocation.getArgument(1);
            if (!principal.canAccess(owner)) throw AppException.notFound("Chatbot not found.");
            return bot;
        });
        when(knowledge.readyDocuments(chatbot)).thenReturn(List.of(new KnowledgeClient.ReadyDocument(document, "Employee Handbook.pdf", "PDF", null, 24,
                List.of(new KnowledgeClient.Chunk(UUID.randomUUID(), document, 0, 12, null, null, "Employees receive 18 days of annual leave each year.")))));
    }

    private String bearer(UUID userId) {
        return "Bearer " + jwt.issue(new UserPrincipal(userId, UUID.randomUUID(), "u@example.com", "USER", 0));
    }

    @Test
    void askSavesHistoryWithCitationsAndStaysScoped() throws Exception {
        String first = mvc.perform(post("/api/chatbots/" + chatbot + "/ask").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the annual leave policy?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("Happy to help!")))
                .andExpect(jsonPath("$.answer", containsString("[1]")))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].document_name", is("Employee Handbook.pdf")))
                .andExpect(jsonPath("$.sources[0].page_number", is(12)))
                .andReturn().getResponse().getContentAsString();
        String conversationId = json.readTree(first).get("conversation_id").asText();

        mvc.perform(post("/api/chatbots/" + chatbot + "/ask").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"And remote work?\",\"conversation_id\":\"" + conversationId + "\",\"model\":\"Qwen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id", is(conversationId)))
                .andExpect(jsonPath("$.answer", containsString("Qwen")));

        mvc.perform(get("/api/chatbots/" + chatbot + "/conversations/" + conversationId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(4)))
                .andExpect(jsonPath("$.messages[1].role", is("ASSISTANT")))
                .andExpect(jsonPath("$.messages[1].sources", hasSize(1)));

        mvc.perform(get("/api/chatbots/" + chatbot + "/conversations").header("Authorization", bearer(stranger))).andExpect(status().isNotFound());

        mvc.perform(post("/api/chatbots/" + chatbot + "/compare").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Refund policy?\",\"models\":[\"Llama 3.2 3B\",\"Qwen\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/internal/stats").header("X-Internal-Token", "change-me-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_messages", is(8)))
                .andExpect(jsonPath("$.messages_by_channel.PLAYGROUND", is(8)));

        mvc.perform(delete("/api/chatbots/" + chatbot + "/conversations/" + conversationId).header("Authorization", bearer(owner))).andExpect(status().isNoContent());
    }

    @Test
    void internalAskKeepsGuestSessionsSeparate() throws Exception {
        UUID integration = UUID.randomUUID();
        String body = "{\"chatbot_id\":\"" + chatbot + "\",\"question\":\"Hello\",\"channel\":\"PUBLIC_LINK\",\"integration_id\":\"" + integration + "\",\"session_hash\":\"abc\"}";
        String first = mvc.perform(post("/api/internal/ask").header("X-Internal-Token", "change-me-internal-token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String conversationId = json.readTree(first).get("conversation_id").asText();
        // A different session cannot continue someone else's conversation: it silently gets a new one.
        String other = body.replace("\"session_hash\":\"abc\"", "\"session_hash\":\"zzz\",\"conversation_id\":\"" + conversationId + "\"");
        mvc.perform(post("/api/internal/ask").header("X-Internal-Token", "change-me-internal-token").contentType(MediaType.APPLICATION_JSON).content(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id", org.hamcrest.Matchers.not(conversationId)));
        mvc.perform(post("/api/internal/ask").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }
}
