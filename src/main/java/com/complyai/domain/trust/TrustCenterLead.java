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
package com.complyai.domain.trust;

import com.complyai.domain.common.BaseEntity;
import com.complyai.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.util.UUID;

/**
 * A prospect who asked for security material through the public Trust Center.
 *
 * <p>Captured anonymously, so treat every field as untrusted input and never echo it back into
 * another tenant's view.
 */
@Entity
@Table(
    name = "trust_center_leads",
    indexes = {
      @Index(name = "idx_trust_lead_tenant", columnList = "tenant_id"),
      @Index(name = "idx_trust_lead_profile", columnList = "profile_id"),
      @Index(name = "idx_trust_lead_status", columnList = "status"),
      @Index(name = "idx_trust_lead_email", columnList = "email")
    })
public class TrustCenterLead extends BaseEntity {

  public enum Status {
    NEW,
    CONTACTED,
    GRANTED,
    DECLINED,
    SPAM
  }

  @Column(name = "profile_id", nullable = false)
  private UUID profileId;

  @Column(name = "name", length = 255, nullable = false)
  private String name;

  @Column(name = "email", length = 320, nullable = false)
  private String email;

  @Column(name = "company", length = 255)
  private String company;

  @Column(name = "role_title", length = 255)
  private String roleTitle;

  @Column(name = "message", columnDefinition = "TEXT")
  private String message;

  /** Which published item the prospect asked about, when they clicked a row. */
  @Column(name = "requested_item_id")
  private UUID requestedItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private Status status = Status.NEW;

  @Column(name = "source_url", length = 1000)
  private String sourceUrl;

  /** Truncated for privacy; used only for abuse rate-limiting. */
  @Column(name = "ip_hash", length = 64)
  private String ipHash;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "tenant_id", insertable = false, updatable = false)
  private UUID tenantId;

  public UUID getProfileId() {
    return profileId;
  }

  public void setProfileId(UUID profileId) {
    this.profileId = profileId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getCompany() {
    return company;
  }

  public void setCompany(String company) {
    this.company = company;
  }

  public String getRoleTitle() {
    return roleTitle;
  }

  public void setRoleTitle(String roleTitle) {
    this.roleTitle = roleTitle;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public UUID getRequestedItemId() {
    return requestedItemId;
  }

  public void setRequestedItemId(UUID requestedItemId) {
    this.requestedItemId = requestedItemId;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public String getIpHash() {
    return ipHash;
  }

  public void setIpHash(String ipHash) {
    this.ipHash = ipHash;
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
