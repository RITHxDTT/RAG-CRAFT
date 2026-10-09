package com.ragcraft.identity;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Runs against the in-memory H2 profile; no Docker required. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void registerLoginProfileAndLogout() throws Exception {
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\"Test Person\",\"email\":\"test@example.com\",\"password\":\"secret1\",\"confirm_password\":\"secret1\",\"terms\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role", is("USER")))
                .andExpect(jsonPath("$.user.organization_name", is("My Workspace")))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(registered).get("access_token").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("test@example.com")));

        mvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"display_name\":\"Tester\",\"theme\":\"dark\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name", is("Tester")))
                .andExpect(jsonPath("$.theme", is("dark")));

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        // The token version was bumped, so the old token is rejected.
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void demoAccountsAreSeededAndAdminRoutesAreProtected() throws Exception {
        String admin = login("admin@gmail.com", "123");
        String user = login("user@gmail.com", "123");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@gmail.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", is("Invalid credentials or disabled account.")));
    }

    @Test
    void internalEndpointsNeedTheServiceToken() throws Exception {
        mvc.perform(get("/api/internal/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/stats").header("X-Internal-Token", "change-me-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_users").isNumber());
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("access_token").asText();
    }
}
