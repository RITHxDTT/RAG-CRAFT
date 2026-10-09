package com.ragcraft.identity.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
