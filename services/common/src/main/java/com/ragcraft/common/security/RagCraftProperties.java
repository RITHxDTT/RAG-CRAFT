package com.ragcraft.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings shared by every service. Values come from environment variables so the same
 * JWT secret and internal token are used across the platform (see docker-compose.microservices.yml).
 */
@Validated
@ConfigurationProperties(prefix = "ragcraft")
public class RagCraftProperties {

    private final Jwt jwt = new Jwt();
    private final Services services = new Services();

    /** Shared secret for service-to-service calls on /api/internal/** endpoints. */
    @NotBlank
    private String internalToken = "change-me-internal-token";

    /** Public URL of the Next.js frontend, used to build share and widget links. */
    private String frontendUrl = "http://localhost:3000";

    /** Public URL of the API gateway, used to build webhook URLs and embed snippets. */
    private String publicApiUrl = "http://localhost:8080";

    public Jwt getJwt() { return jwt; }
    public Services getServices() { return services; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String frontendUrl) { this.frontendUrl = frontendUrl; }
    public String getPublicApiUrl() { return publicApiUrl; }
    public void setPublicApiUrl(String publicApiUrl) { this.publicApiUrl = publicApiUrl; }

    public static class Jwt {
        /** HS256 secret; must match JWT_SECRET of the FastAPI backend to keep tokens interoperable. */
        @NotBlank
        @Size(min = 32)
        private String secret = "development-only-secret-change-me-please-32chars";
        private String issuer = "rag-craft";
        private String audience = "rag-craft-admin";
        private int sessionMinutes = 480;
        private String cookieName = "ragcraft_session";
        private boolean cookieSecure = false;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public int getSessionMinutes() { return sessionMinutes; }
        public void setSessionMinutes(int sessionMinutes) { this.sessionMinutes = sessionMinutes; }
        public String getCookieName() { return cookieName; }
        public void setCookieName(String cookieName) { this.cookieName = cookieName; }
        public boolean isCookieSecure() { return cookieSecure; }
        public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
    }

    /** Base URLs of the other services. Each one is overridable per environment. */
    public static class Services {
        private String identity = "http://localhost:8081";
        private String catalog = "http://localhost:8082";
        private String chatbot = "http://localhost:8083";
        private String knowledge = "http://localhost:8084";
        private String conversation = "http://localhost:8085";
        private String channel = "http://localhost:8086";
        private String analytics = "http://localhost:8087";

        public String getIdentity() { return identity; }
        public void setIdentity(String identity) { this.identity = identity; }
        public String getCatalog() { return catalog; }
        public void setCatalog(String catalog) { this.catalog = catalog; }
        public String getChatbot() { return chatbot; }
        public void setChatbot(String chatbot) { this.chatbot = chatbot; }
        public String getKnowledge() { return knowledge; }
        public void setKnowledge(String knowledge) { this.knowledge = knowledge; }
        public String getConversation() { return conversation; }
        public void setConversation(String conversation) { this.conversation = conversation; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getAnalytics() { return analytics; }
        public void setAnalytics(String analytics) { this.analytics = analytics; }
    }
}
