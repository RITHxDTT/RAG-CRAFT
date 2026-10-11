package com.ragcraft.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RouteTableTest {

    @Autowired RouteTable routes;

    @Test
    void everyFrontendPathLandsOnTheOwningService() {
        assertThat(routes.resolve("/api/auth/login").orElseThrow().service()).isEqualTo("identity");
        assertThat(routes.resolve("/api/admin/users/123").orElseThrow().service()).isEqualTo("identity");
        assertThat(routes.resolve("/api/models").orElseThrow().service()).isEqualTo("catalog");
        assertThat(routes.resolve("/api/admin/prompt-templates/1").orElseThrow().service()).isEqualTo("catalog");
        assertThat(routes.resolve("/api/dashboard").orElseThrow().service()).isEqualTo("chatbot");
        assertThat(routes.resolve("/api/chatbots").orElseThrow().service()).isEqualTo("chatbot");
        assertThat(routes.resolve("/api/chatbots/abc").orElseThrow().service()).isEqualTo("chatbot");
        assertThat(routes.resolve("/api/admin/chatbots").orElseThrow().service()).isEqualTo("chatbot");
        assertThat(routes.resolve("/api/chatbots/abc/documents").orElseThrow().service()).isEqualTo("knowledge");
        assertThat(routes.resolve("/api/chatbots/abc/documents/def/download").orElseThrow().service()).isEqualTo("knowledge");
        assertThat(routes.resolve("/api/knowledge/recent").orElseThrow().service()).isEqualTo("knowledge");
        assertThat(routes.resolve("/api/chatbots/abc/ask").orElseThrow().service()).isEqualTo("conversation");
        assertThat(routes.resolve("/api/chatbots/abc/conversations/x").orElseThrow().service()).isEqualTo("conversation");
        assertThat(routes.resolve("/api/chatbots/abc/channels").orElseThrow().service()).isEqualTo("channel");
        assertThat(routes.resolve("/api/public/share/token/ask").orElseThrow().service()).isEqualTo("channel");
        assertThat(routes.resolve("/api/webhooks/telegram/token").orElseThrow().service()).isEqualTo("channel");
        assertThat(routes.resolve("/api/analytics").orElseThrow().service()).isEqualTo("analytics");
        // V5 paths
        assertThat(routes.resolve("/api/quota").orElseThrow().service()).isEqualTo("identity");
        assertThat(routes.resolve("/api/admin/audit-logs/abc").orElseThrow().service()).isEqualTo("identity");
        assertThat(routes.resolve("/api/auth/verify-email").orElseThrow().service()).isEqualTo("identity");
        assertThat(routes.resolve("/api/embedding-models").orElseThrow().service()).isEqualTo("catalog");
        assertThat(routes.resolve("/api/settings-limits").orElseThrow().service()).isEqualTo("catalog");
        assertThat(routes.resolve("/api/admin/settings-limits/top_k").orElseThrow().service()).isEqualTo("catalog");
        assertThat(routes.resolve("/api/chatbots/abc/publish").orElseThrow().service()).isEqualTo("chatbot");
        assertThat(routes.resolve("/api/chatbots/abc/appeals").orElseThrow().service()).isEqualTo("chatbot");
        assertThat(routes.resolve("/api/admin/appeals/abc/approve").orElseThrow().service()).isEqualTo("chatbot");
        assertThat(routes.resolve("/api/admin/chatbots/abc/disable").orElseThrow().service()).isEqualTo("chatbot");
        assertThat(routes.resolve("/api/admin/analytics").orElseThrow().service()).isEqualTo("analytics");
    }

    @Test
    void internalAndUnknownPathsAreNotRouted() {
        assertThat(routes.resolve("/api/internal/chatbots/abc")).isEmpty();
        assertThat(routes.resolve("/api/nothing")).isEmpty();
    }
}
