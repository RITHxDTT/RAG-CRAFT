package com.ragcraft.identity.api;

import com.ragcraft.common.security.CurrentUser;
import com.ragcraft.identity.api.IdentityApi.QuotaResponse;
import com.ragcraft.identity.service.QuotaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final QuotaService quotas;
    private final CurrentUser currentUser;

    public QuotaController(QuotaService quotas, CurrentUser currentUser) {
        this.quotas = quotas;
        this.currentUser = currentUser;
    }

    /** Bots and storage, used / limit, for the signed-in user. */
    @GetMapping
    public QuotaResponse mine() {
        return quotas.usage(currentUser.require().userId());
    }
}
