package com.ragcraft.identity;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** With identity.auto-verify-email=false, a new account cannot sign in until it confirms the link. */
@SpringBootTest(properties = "identity.auto-verify-email=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class V5VerificationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void unverifiedAccountsMustConfirmTheirEmail() throws Exception {
        String created = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"full_name\":\"Late\",\"email\":\"late@example.com\",\"password\":\"late-pass-12\",\"confirm_password\":\"late-pass-12\",\"terms\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.status", is("UNVERIFIED"))).andExpect(jsonPath("$.access_token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(created).get("verification_token").asText();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"late@example.com\",\"password\":\"late-pass-12\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code", is("UNVERIFIED")));
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"nope\"}")).andExpect(status().isNotFound());
        // Resending replaces the previous link, so the first one stops working.
        String resent = mvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"late@example.com\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}")).andExpect(status().isGone());
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + json.readTree(resent).get("reset_token").asText() + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"late@example.com\",\"password\":\"late-pass-12\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.status", is("ACTIVE")));
        // A generic answer for accounts that do not exist.
        mvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ghost@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.reset_token").doesNotExist());
    }
}
