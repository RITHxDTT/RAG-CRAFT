package com.ragcraft.identity.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Per-user quota override; users without a row use {@link QuotaDefaults}. */
@Entity
@Table(name = "user_quotas")
public class UserQuota extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "max_bots", nullable = false)
    private int maxBots;

    @Column(name = "max_storage_bytes", nullable = false)
    private long maxStorageBytes;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public int getMaxBots() { return maxBots; }
    public void setMaxBots(int maxBots) { this.maxBots = maxBots; }
    public long getMaxStorageBytes() { return maxStorageBytes; }
    public void setMaxStorageBytes(long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
