package com.ragcraft.identity.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** The single row holding the quota applied to every user without an override. */
@Entity
@Table(name = "quota_defaults")
public class QuotaDefaults extends BaseEntity {

    @Column(nullable = false, unique = true)
    private boolean singleton = true;

    @Column(name = "max_bots", nullable = false)
    private int maxBots = 5;

    @Column(name = "max_storage_bytes", nullable = false)
    private long maxStorageBytes = 5368709120L;

    public int getMaxBots() { return maxBots; }
    public void setMaxBots(int maxBots) { this.maxBots = maxBots; }
    public long getMaxStorageBytes() { return maxStorageBytes; }
    public void setMaxStorageBytes(long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }
}
