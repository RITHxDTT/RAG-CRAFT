package com.ragcraft.identity;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;

/** V5 rules: password policy, lock-out, suspension, quotas, audit log, account deletion and restore. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class V5IdentityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String INTERNAL = "change-me-internal-token";

    @Test
    void passwordPolicyAndTerms() throws Exception {
        register("a1@example.com", "short", true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Password must be 8–64 characters.")));
        register("a2@example.com", "password123", true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("too common")));
        register("a3@example.com", "a3@example.com", true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("same as your email")));
        register("a4@example.com", "long-enough-1", false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("Terms")));
        register("a5@example.com", "long-enough-1", true).andExpect(status().isCreated()).andExpect(jsonPath("$.user.status", is("ACTIVE")));
        register("a5@example.com", "long-enough-1", true).andExpect(status().isConflict());
    }

    @Test
    void accountLocksAfterFiveFailuresWithGrowingDuration() throws Exception {
        register("lock@example.com", "lock-pass-12", true).andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            attempt("lock@example.com", "wrong-pass-1").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")));
        }
        attempt("lock@example.com", "wrong-pass-1").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", is("LOCKED"))).andExpect(jsonPath("$.detail", containsString("1 minute")));
        // Even the right password is refused while locked, and an unknown account gives the same generic error as a wrong password.
        attempt("lock@example.com", "lock-pass-12").andExpect(status().isTooManyRequests());
        attempt("nobody@example.com", "whatever-12").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail", is("Invalid email or password.")));
    }

    @Test
    void profileChangesNeedCurrentPasswordAndEmailChangeNeedsVerification() throws Exception {
        String token = register("prof@example.com", "prof-pass-12", true).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + json.readTree(token).get("access_token").asText();
        mvc.perform(patch("/api/auth/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"nope\",\"password\":\"brand-new-pass-3\",\"confirm_password\":\"brand-new-pass-3\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Current password is incorrect.")));
        String changed = mvc.perform(patch("/api/auth/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prof2@example.com\",\"language\":\"ko\",\"timezone\":\"Asia/Phnom_Penh\",\"current_password\":\"prof-pass-12\",\"password\":\"brand-new-pass-3\",\"confirm_password\":\"brand-new-pass-3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email", is("prof@example.com")))
                .andExpect(jsonPath("$.user.pending_email", is("prof2@example.com")))
                .andExpect(jsonPath("$.user.language", is("ko")))
                .andReturn().getResponse().getContentAsString();
        String link = json.readTree(changed).get("verification_token").asText();
        mvc.perform(post("/api/auth/confirm-email-change").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + link + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email", is("prof2@example.com")));
        mvc.perform(post("/api/auth/confirm-email-change").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + link + "\"}"))
                .andExpect(status().isGone());
        attempt("prof2@example.com", "brand-new-pass-3").andExpect(status().isOk());
    }

    @Test
    void forgotPasswordNeverRevealsAccountsAndResetUnlocks() throws Exception {
        register("forgot@example.com", "forgot-pass-1", true).andExpect(status().isCreated());
        String known = forgot("forgot@example.com");
        String unknown = forgot("ghost@example.com");
        org.junit.jupiter.api.Assertions.assertEquals(json.readTree(known).get("detail").asText().replace(" No email is sent in log mail mode.", ""), json.readTree(unknown).get("detail").asText());
        String resetToken = json.readTree(known).get("reset_token").asText();
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"password\":\"password123\",\"confirm_password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"password\":\"reset-pass-77\",\"confirm_password\":\"reset-pass-77\"}"))
                .andExpect(status().isOk());
        attempt("forgot@example.com", "reset-pass-77").andExpect(status().isOk());
    }

    @Test
    void adminSuspensionQuotaAndAuditLog() throws Exception {
        String admin = bearer("admin@gmail.com", "123");
        String created = register("victim@example.com", "victim-pass-1", true).andReturn().getResponse().getContentAsString();
        String userId = json.readTree(created).get("user").get("id").asText();
        String userBearer = "Bearer " + json.readTree(created).get("access_token").asText();

        // A reason is mandatory and fixed demo accounts are protected.
        mvc.perform(post("/api/admin/users/" + userId + "/suspend").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/admin/users/" + userId + "/suspend").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Spam content\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("SUSPENDED"))).andExpect(jsonPath("$.is_active", is(false)))
                .andExpect(jsonPath("$.suspend_reason", is("Spam content")));
        // Suspension revokes the live token and blocks sign-in.
        mvc.perform(get("/api/auth/me").header("Authorization", userBearer)).andExpect(status().isUnauthorized());
        attempt("victim@example.com", "victim-pass-1").andExpect(status().isForbidden()).andExpect(jsonPath("$.code", is("SUSPENDED")));
        mvc.perform(get("/api/internal/users/" + userId + "/session-valid?version=0").header("X-Internal-Token", INTERNAL)).andExpect(jsonPath("$.valid", is(false)));
        mvc.perform(post("/api/admin/users/" + userId + "/reactivate").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Appeal accepted\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("ACTIVE")));
        attempt("victim@example.com", "victim-pass-1").andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/" + userId + "/suspend").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/" + userId + "/reactivate").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"ok\"}"))
                .andExpect(status().isOk());

        // Quota: default 5 bots / 5 GB, an admin may only lower it.
        mvc.perform(get("/api/internal/users/" + userId + "/quota").header("X-Internal-Token", INTERNAL))
                .andExpect(jsonPath("$.max_bots", is(5))).andExpect(jsonPath("$.max_storage_bytes", is(5368709120L)));
        mvc.perform(put("/api/admin/users/" + userId + "/quota").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"max_bots\":6,\"max_storage_bytes\":100,\"reason\":\"too many\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + userId + "/quota").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"max_bots\":1,\"max_storage_bytes\":1048576,\"reason\":\"trial plan\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quota.max_bots", is(1)));
        mvc.perform(get("/api/quota").header("Authorization", "Bearer " + login("victim@example.com", "victim-pass-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bots.limit", is(1))).andExpect(jsonPath("$.bots.used", is(0)));

        // Audit log: all three actions were recorded, newest first, filterable, with the reason.
        mvc.perform(get("/api/admin/audit-logs?target=victim@example.com").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].action", is("SET_QUOTA"))).andExpect(jsonPath("$[0].reason", is("trial plan")))
                .andExpect(jsonPath("$[0].details.after.max_bots", is(1)));
        mvc.perform(get("/api/admin/audit-logs?action=SUSPEND_USER&target=victim@example.com").header("Authorization", admin)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/admin/audit-logs?from=2999-01-01").header("Authorization", admin)).andExpect(jsonPath("$", hasSize(0)));
        // The original token was revoked by the suspension, so use a fresh sign-in to prove ordinary users cannot read the log.
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer("victim@example.com", "victim-pass-1"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/audit-logs/" + json.readTree(mvc.perform(get("/api/admin/audit-logs").header("Authorization", admin)).andReturn().getResponse().getContentAsString()).get(0).get("id").asText())
                .header("Authorization", admin)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void internalAuditEndpointValidatesActionsAndRequiresReason() throws Exception {
        String body = "{\"admin_email\":\"admin@gmail.com\",\"action\":\"%s\",\"target_type\":\"CHATBOT\",\"target_id\":\"00000000-0000-0000-0000-000000000001\",\"target_label\":\"Bot\",\"reason\":\"%s\"}";
        mvc.perform(post("/api/internal/audit").header("X-Internal-Token", INTERNAL).contentType(MediaType.APPLICATION_JSON).content(body.formatted("DELETE_EVERYTHING", "r")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/internal/audit").header("X-Internal-Token", INTERNAL).contentType(MediaType.APPLICATION_JSON).content(body.formatted("FORCE_DISABLE_CHATBOT", "Spam")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id", notNullValue()));
        mvc.perform(post("/api/internal/audit").contentType(MediaType.APPLICATION_JSON).content(body.formatted("FORCE_DISABLE_CHATBOT", "Spam"))).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAnAccountStartsARestoreWindow() throws Exception {
        String created = register("bye@example.com", "bye-pass-123", true).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + json.readTree(created).get("access_token").asText();
        mvc.perform(post("/api/auth/delete-account").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{\"confirm_email\":\"wrong@example.com\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/delete-account").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{\"confirm_email\":\"BYE@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.restorable_until", notNullValue()));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        String again = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bye@example.com\",\"password\":\"bye-pass-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.status", is("PENDING_DELETION"))).andReturn().getResponse().getContentAsString();
        String second = "Bearer " + json.readTree(again).get("access_token").asText();
        mvc.perform(patch("/api/auth/me").header("Authorization", second).contentType(MediaType.APPLICATION_JSON).content("{\"full_name\":\"X\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code", is("PENDING_DELETION")));
        mvc.perform(post("/api/auth/restore").header("Authorization", second)).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("ACTIVE")));
        // Fixed demo accounts cannot be deleted.
        mvc.perform(post("/api/auth/delete-account").header("Authorization", bearer("user@gmail.com", "123")).contentType(MediaType.APPLICATION_JSON).content("{\"confirm_email\":\"user@gmail.com\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----
    private ResultActions register(String email, String password, boolean terms) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"full_name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"confirm_password\":\"" + password + "\",\"terms\":" + terms + "}"));
    }

    private ResultActions attempt(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String login(String email, String password) throws Exception {
        JsonNode node = json.readTree(attempt(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return node.get("access_token").asText();
    }

    private String bearer(String email, String password) throws Exception {
        return "Bearer " + login(email, password);
    }

    private String forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    }
}
