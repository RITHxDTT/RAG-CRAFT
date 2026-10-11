package com.ragcraft.identity.service;

import com.ragcraft.identity.IdentityProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly: erases accounts whose 7-day restore window after "delete my account" has passed. */
@Component
public class AccountPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeJob.class);
    private final AdminUserService admin;
    private final IdentityProperties properties;

    public AccountPurgeJob(AdminUserService admin, IdentityProperties properties) {
        this.admin = admin;
        this.properties = properties;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void run() {
        int removed = admin.purgeExpired(Instant.now().minus(Duration.ofDays(properties.getDeletionGraceDays())));
        if (removed > 0) log.info("Permanently deleted {} accounts past the restore window", removed);
    }
}
