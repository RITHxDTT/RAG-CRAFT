package com.ragcraft.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
