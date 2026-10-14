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
package com.quenaire.domain.program;

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One observed fact about a tenant's live security program — "MFA is enforced on the AWS root
 * account", "branch protection requires review on 14 repos", "the incident-response runbook was
 * reviewed 3 weeks ago".
 *
 * <p>Signals are evidence, not claims: each carries the system it came from and when it was last
 * seen, so an answer grounded in one can be dated and audited.
 */
@Entity
@Table(
    name = "program_signals",
    indexes = {
      @Index(name = "idx_program_signal_tenant", columnList = "tenant_id"),
      @Index(name = "idx_program_signal_source", columnList = "source"),
      @Index(name = "idx_program_signal_status", columnList = "status"),
      @Index(name = "idx_program_signal_observed", columnList = "observed_at")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_program_signal_identity",
          columnNames = {"tenant_id", "source", "external_id"})
    })
public class ProgramSignal extends BaseEntity {

  public enum Source {
    AWS,
    GITHUB,
    JIRA,
    CONFLUENCE,
    MANUAL
  }

  /** Whether the observed state is what a reviewer would want to see. */
  public enum Status {
    /** Control is in place and healthy. */
    PASSING,
    /** Control exists but is degraded, partial, or overdue. */
    WARNING,
    /** Control is absent or actively failing. */
    FAILING,
    /** Informational — describes the program without asserting health. */
    INFORMATIONAL
  }

  @Enumerated(EnumType.STRING)
  @Column(name = "source", length = 32, nullable = false)
  private Source source;

  /**
   * Stable id within the source system (a Config rule ARN, a repo name, a Jira project key).
   * Together with tenant + source this de-duplicates re-ingests.
   */
  @Column(name = "external_id", length = 512, nullable = false)
  private String externalId;

  @Column(name = "title", length = 500, nullable = false)
  private String title;

  @Column(name = "detail", columnDefinition = "TEXT")
  private String detail;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private Status status = Status.INFORMATIONAL;

  /** Free-form category used to match signals to questionnaire topics. */
  @Column(name = "category", length = 255)
  private String category;

  @Column(name = "resource_type", length = 255)
  private String resourceType;

  @Column(name = "source_url", length = 1000)
  private String sourceUrl;

  /** When the source system last reported this state. */
  @Column(name = "observed_at", nullable = false)
  private OffsetDateTime observedAt = OffsetDateTime.now();

  @Column(name = "raw_json", columnDefinition = "TEXT")
  private String rawJson;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
  private Tenant tenant;

  // This field writes tenant_id; the association above only reads it, so setTenantId persists.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  /** A signal not refreshed recently should not be quoted as current fact. */
  public boolean isStale(int maxAgeDays) {
    return observedAt.isBefore(OffsetDateTime.now().minusDays(maxAgeDays));
  }

  public Source getSource() {
    return source;
  }

  public void setSource(Source source) {
    this.source = source;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public OffsetDateTime getObservedAt() {
    return observedAt;
  }

  public void setObservedAt(OffsetDateTime observedAt) {
    this.observedAt = observedAt;
  }

  public String getRawJson() {
    return rawJson;
  }

  public void setRawJson(String rawJson) {
    this.rawJson = rawJson;
  }

  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
    this.tenantId = tenant != null ? tenant.getId() : null;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }
}
