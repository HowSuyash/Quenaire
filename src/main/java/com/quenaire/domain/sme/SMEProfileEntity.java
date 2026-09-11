/*
 * Copyright (c) 2026 Quenaire. All rights reserved.
 *
 * This file is proprietary and confidential. It is part of an unpublished work
 * owned by Quenaire and is protected by copyright and trade secret law.
 *
 * No licence, express or implied, is granted. No part of this file may be
 * copied, modified, distributed, disclosed, or used to create derivative works
 * without the prior written permission of Quenaire.
 *
 * Unauthorised copying or distribution of this file, via any medium, is
 * strictly prohibited.
 */
package com.quenaire.domain.sme;

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A subject-matter expert who can be routed questions for review.
 *
 * <p>Replaces the previous in-memory {@code smeRegistry} map so routing survives restarts and is
 * scoped per tenant.
 */
@Entity
@Table(
    name = "sme_profiles",
    indexes = {
      @Index(name = "idx_sme_profile_tenant", columnList = "tenant_id"),
      @Index(name = "idx_sme_profile_user", columnList = "user_id"),
      @Index(name = "idx_sme_profile_active", columnList = "is_active")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_sme_profile_tenant_user",
          columnNames = {"tenant_id", "user_id"})
    })
public class SMEProfileEntity extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "email", length = 320, nullable = false)
  private String email;

  @Column(name = "full_name", length = 255)
  private String fullName;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "sme_profile_expertise",
      joinColumns = @JoinColumn(name = "sme_profile_id"))
  @Column(name = "tag", length = 100)
  private List<String> expertiseTags = new ArrayList<>();

  @Column(name = "slack_id", length = 64)
  private String slackId;

  @Column(name = "max_concurrent_reviews", nullable = false)
  private Integer maxConcurrentReviews = 10;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @Column(name = "last_active_at")
  private OffsetDateTime lastActiveAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "tenant_id", insertable = false, updatable = false)
  private UUID tenantId;

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public List<String> getExpertiseTags() {
    return expertiseTags;
  }

  public void setExpertiseTags(List<String> expertiseTags) {
    this.expertiseTags = expertiseTags;
  }

  public String getSlackId() {
    return slackId;
  }

  public void setSlackId(String slackId) {
    this.slackId = slackId;
  }

  public Integer getMaxConcurrentReviews() {
    return maxConcurrentReviews;
  }

  public void setMaxConcurrentReviews(Integer maxConcurrentReviews) {
    this.maxConcurrentReviews = maxConcurrentReviews;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public OffsetDateTime getLastActiveAt() {
    return lastActiveAt;
  }

  public void setLastActiveAt(OffsetDateTime lastActiveAt) {
    this.lastActiveAt = lastActiveAt;
  }

  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }
}
