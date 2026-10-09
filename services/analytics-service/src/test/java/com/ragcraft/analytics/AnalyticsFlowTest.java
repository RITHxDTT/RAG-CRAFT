package com.ragcraft.analytics;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ragcraft.analytics.client.StatsClient;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.security.JwtService;
import com.ragcraft.common.security.UserPrincipal;
import java.time.Instant;
import java.util.List;
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
class AnalyticsFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean StatsClient stats;

    final UUID owner = UUID.randomUUID();
    final UUID chatbot = UUID.randomUUID();

    @BeforeEach
    void stubs() {
        when(stats.chatbots(any())).thenReturn(new StatsClient.ChatbotStats(1, 1));
        when(stats.chatbotList(any())).thenReturn(List.of(new ChatbotSummary(chatbot, null, owner, "Company Assistant", "", null, "ACTIVE", List.of(), null)));
        when(stats.knowledge(any())).thenReturn(new StatsClient.KnowledgeStats(3, 3, 3, 0,
                List.of(new StatsClient.NamedCount("PDF", 2), new StatsClient.NamedCount("MD", 1)), List.of(new StatsClient.NamedCount("READY", 3))));
        when(stats.conversations(any())).thenReturn(new StatsClient.ConversationStats(2, 8, 8, Map.of("PLAYGROUND", 6L, "PUBLIC_LINK", 2L), Map.of(chatbot, 8L)));
        when(stats.channels(any())).thenReturn(new StatsClient.ChannelStats(3, 1, List.of(new StatsClient.NamedCount("PUBLIC_LINK", 1))));
        when(stats.identity()).thenReturn(new StatsClient.IdentityStats(12, 10));
    }

    private String bearer(String role) {
        return "Bearer " + jwt.issue(new UserPrincipal(owner, UUID.randomUUID(), "u@example.com", role, 0));
    }

    @Test
    void eventsFeedTheDailySeriesAndOverviewAggregatesServices() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/internal/events").header("X-Internal-Token", "change-me-internal-token").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"owner_id\":\"" + owner + "\",\"chatbot_id\":\"" + chatbot + "\",\"channel\":\"" + (i == 0 ? "PUBLIC_LINK" : "PLAYGROUND")
                                    + "\",\"messages\":2,\"created_at\":\"" + Instant.now() + "\"}"))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(get("/api/analytics?days=7").header("Authorization", bearer("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage.total_messages", is(8)))
                .andExpect(jsonPath("$.usage.total_users").doesNotExist())
                .andExpect(jsonPath("$.daily", hasSize(7)))
                .andExpect(jsonPath("$.daily[6].messages", is(6)))
                .andExpect(jsonPath("$.daily[6].playground", is(4)))
                .andExpect(jsonPath("$.daily[6].published", is(2)))
                .andExpect(jsonPath("$.chatbots[0].name", is("Company Assistant")))
                .andExpect(jsonPath("$.channel_status[0].name", is("Live")))
                .andExpect(jsonPath("$.source_types", hasSize(2)));

        mvc.perform(get("/api/admin/analytics").header("Authorization", bearer("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/analytics").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage.total_users", is(12)));
    }
}
