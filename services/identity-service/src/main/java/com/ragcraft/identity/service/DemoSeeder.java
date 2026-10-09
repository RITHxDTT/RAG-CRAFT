package com.ragcraft.identity.service;

import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.identity.IdentityProperties;
import com.ragcraft.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the fixed demo accounts (admin@gmail.com / user@gmail.com, password 123) when
 * identity.seed-demo-accounts is true, and an admin from ADMIN_EMAIL / ADMIN_PASSWORD when provided.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final IdentityProperties properties;
    private final UserRepository users;
    private final AuthService auth;

    public DemoSeeder(IdentityProperties properties, UserRepository users, AuthService auth) {
        this.properties = properties;
        this.users = users;
        this.auth = auth;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isSeedDemoAccounts()) {
            ensure("admin@gmail.com", "123", "Demo Administrator", UserPrincipal.ROLE_ADMIN);
            ensure("user@gmail.com", "123", "Demo User", UserPrincipal.ROLE_USER);
        }
        if (!properties.getAdminEmail().isBlank() && !properties.getAdminPassword().isBlank()) {
            ensure(properties.getAdminEmail(), properties.getAdminPassword(), "Administrator", UserPrincipal.ROLE_ADMIN);
        }
    }

    private void ensure(String email, String password, String name, String role) {
        if (users.existsByEmailIgnoreCase(email)) return;
        auth.createUser(email, password, name, role);
        log.info("Seeded {} account {}", role, email);
    }
}
