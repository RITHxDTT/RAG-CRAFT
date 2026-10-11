package com.ragcraft.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A browser accepts exactly one Access-Control-Allow-Origin. Downstream services run their own CORS filter and answer with that header too,
 * so the gateway must not pass theirs through. curl and server-side clients do not enforce this, which is how the bug went unnoticed.
 */
@SpringBootTest(properties = "gateway.cors-origins=http://localhost:3000")
@AutoConfigureMockMvc
class CorsProxyTest {

    static final AtomicReference<String> seenOrigin = new AtomicReference<>("none");
    static final HttpServer downstream = start();

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                seenOrigin.set(String.valueOf(exchange.getRequestHeaders().getFirst("Origin")));
                // What a Spring service with its own CorsFilter returns for a request that carries an Origin header.
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "http://localhost:3000");
                exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("gateway.services.identity", () -> "http://127.0.0.1:" + downstream.getAddress().getPort());
    }

    @AfterAll
    static void stop() { downstream.stop(0); }

    @Autowired MockMvc mvc;

    @Test
    void theBrowserSeesExactlyOneAllowOriginHeaderAndTheDownstreamNeverSeesTheOrigin() throws Exception {
        var response = mvc.perform(get("/api/auth/me").header(HttpHeaders.ORIGIN, "http://localhost:3000")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeaders(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).containsExactly("http://localhost:3000");
        assertThat(response.getHeaders(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).hasSize(1);
        assertThat(seenOrigin.get()).isEqualTo("null");
    }

    @Test
    void preflightIsAnsweredByTheGatewayAndOtherOriginsAreRefused() throws Exception {
        var allowed = mvc.perform(options("/api/chatbots/abc").header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH").header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(allowed.getHeaders(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).containsExactly("http://localhost:3000");
        mvc.perform(options("/api/chatbots/abc").header(HttpHeaders.ORIGIN, "http://evil.example").header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void headerRules() {
        assertThat(ProxyController.forwardResponseHeader("Access-Control-Allow-Origin")).isFalse();
        assertThat(ProxyController.forwardResponseHeader("access-control-expose-headers")).isFalse();
        assertThat(ProxyController.forwardResponseHeader("Content-Disposition")).isTrue();
        assertThat(ProxyController.forwardResponseHeader("Content-Length")).isFalse();
        assertThat(ProxyController.forwardRequestHeader("Origin")).isFalse();
        assertThat(ProxyController.forwardRequestHeader("Access-Control-Request-Method")).isFalse();
        assertThat(ProxyController.forwardRequestHeader("Authorization")).isTrue();
        assertThat(ProxyController.forwardRequestHeader("X-Internal-Token")).isTrue();
    }
}
