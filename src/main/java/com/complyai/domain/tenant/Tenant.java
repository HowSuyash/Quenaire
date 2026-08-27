/*
 * Copyright (c) 2026 ComplyAI. All rights reserved.
 *
 * This file is proprietary and confidential. It is part of an unpublished work
 * owned by ComplyAI and is protected by copyright and trade secret law.
 *
 * No licence, express or implied, is granted. No part of this file may be
 * copied, modified, distributed, disclosed, or used to create derivative works
 * without the prior written permission of ComplyAI.
 *
 * Unauthorised copying or distribution of this file, via any medium, is
 * strictly prohibited.
 */
package com.complyai.domain.tenant;

import com.complyai.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "tenants",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_tenants_slug", columnNames = "slug"),
      @UniqueConstraint(name = "uk_tenants_domain", columnNames = "custom_domain")
    })
public class Tenant extends BaseEntity {

  @NotBlank
  @Size(max = 100)
  @Column(name = "name", nullable = false)
  private String name;

  @NotBlank
  @Size(max = 50)
  @Column(name = "slug", nullable = false)
  private String slug;

  @Size(max = 255)
  @Column(name = "custom_domain", unique = true)
  private String customDomain;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TenantStatus status = TenantStatus.ACTIVE;

  @Enumerated(EnumType.STRING)
  @Column(name = "plan", nullable = false)
  private TenantPlan plan = TenantPlan.FREE;

  @Column(name = "plan_expires_at")
  private Instant planExpiresAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "settings_json", columnDefinition = "jsonb")
  private String settingsJson = "{}";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "branding_json", columnDefinition = "jsonb")
  private String brandingJson = "{}";

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public String getCustomDomain() {
    return customDomain;
  }

  public void setCustomDomain(String customDomain) {
    this.customDomain = customDomain;
  }

  public TenantStatus getStatus() {
    return status;
  }

  public void setStatus(TenantStatus status) {
    this.status = status;
  }

  public TenantPlan getPlan() {
    return plan;
  }

  public void setPlan(TenantPlan plan) {
    this.plan = plan;
  }

  public Instant getPlanExpiresAt() {
    return planExpiresAt;
  }

  public void setPlanExpiresAt(Instant planExpiresAt) {
    this.planExpiresAt = planExpiresAt;
  }

  public String getSettingsJson() {
    return settingsJson;
  }

  public void setSettingsJson(String settingsJson) {
    this.settingsJson = settingsJson;
  }

  public String getBrandingJson() {
    return brandingJson;
  }

  public void setBrandingJson(String brandingJson) {
    this.brandingJson = brandingJson;
  }
}
