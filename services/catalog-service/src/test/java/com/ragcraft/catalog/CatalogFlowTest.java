package com.ragcraft.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragcraft.common.security.JwtService;
import com.ragcraft.common.security.UserPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CatalogFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper json;

    private String token(String role) {
        return "Bearer " + jwt.issue(new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), role.toLowerCase() + "@example.com", role, 0));
    }

    @Test
    void seededCatalogIsReadableAndAdminCanManageDefaults() throws Exception {
        mvc.perform(get("/api/models").header("Authorization", token("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].is_default", is(true)));
        mvc.perform(post("/api/admin/models").header("Authorization", token("USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\",\"model_identifier\":\"x\"}"))
                .andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/admin/models").header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mistral\",\"model_identifier\":\"mistral:7b\",\"is_default\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.is_default", is(true)))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();

        // The previous default lost its flag, so only one default remains.
        mvc.perform(get("/api/internal/models/default").header("X-Internal-Token", "change-me-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)));

        mvc.perform(patch("/api/admin/models/" + id).header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mistral\",\"model_identifier\":\"mistral:7b\",\"enabled\":false,\"is_default\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/models").header("Authorization", token("USER")))
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/prompt-templates").header("Authorization", token("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Helpful assistant")));
    }

    @Test
    void embeddingModelsAreSeparateAndLimitsAreAdminControlled() throws Exception {
        // Users see enabled embedding models in their own list, never mixed into the chat models.
        mvc.perform(get("/api/embedding-models").header("Authorization", token("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].kind", is("EMBEDDING"))).andExpect(jsonPath("$[0].embedding_dimensions", is(768)));
        mvc.perform(post("/api/admin/models").header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"E\",\"model_identifier\":\"e:1\",\"kind\":\"EMBEDDING\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/models").header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"E\",\"model_identifier\":\"e:1\",\"kind\":\"WHATEVER\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/internal/embedding-models/default").header("X-Internal-Token", "change-me-internal-token"))
                .andExpect(jsonPath("$.name", is("nomic-embed-text")));

        // Advanced setting limits: readable by users, writable by admins only, always min <= default <= max within absolute bounds.
        mvc.perform(get("/api/settings-limits").header("Authorization", token("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[?(@.key=='temperature')].default_value", org.hamcrest.Matchers.contains(0.4)))
                .andExpect(jsonPath("$[?(@.key=='top_k')].max", org.hamcrest.Matchers.contains(20.0)));
        String body = "{\"min\":%s,\"max\":%s,\"default_value\":%s}";
        mvc.perform(put("/api/admin/settings-limits/top_k").header("Authorization", token("USER")).contentType(MediaType.APPLICATION_JSON).content(body.formatted(1, 10, 3)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/settings-limits/temperature").header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body.formatted(0, 2, "0.4")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/settings-limits/top_k").header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body.formatted(5, 10, 3)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/settings-limits/nonsense").header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body.formatted(1, 10, 3)))
                .andExpect(status().isNotFound());
        String saved = mvc.perform(put("/api/admin/settings-limits/top_k").header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body.formatted(1, 10, 3)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // DECIMAL columns serialise as 10 or 10.0, so compare numerically.
        org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal(json.readTree(saved).get("max").asText()).compareTo(java.math.BigDecimal.TEN));
        String internal = mvc.perform(get("/api/internal/settings-limits").header("X-Internal-Token", "change-me-internal-token")).andReturn().getResponse().getContentAsString();
        for (com.fasterxml.jackson.databind.JsonNode row : json.readTree(internal)) {
            if (row.get("key").asText().equals("top_k")) org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal(row.get("max").asText()).compareTo(java.math.BigDecimal.TEN));
        }
    }
}
