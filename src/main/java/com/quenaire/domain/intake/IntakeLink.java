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
package com.quenaire.domain.intake;

import com.quenaire.domain.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A single-use link through which someone without an account sends a customer a questionnaire.
 *
 * <p>Holds a hash of the token, never the token itself. Everything prefixed {@code submitter} was
 * typed by an anonymous visitor: treat it as untrusted input.
 */
@Entity
@Table(name = "intake_links")
public class IntakeLink extends BaseEntity {

  public enum State {
    PENDING,
    SUBMITTED,
    EXPIRED
  }

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "token_hash", length = 64, nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "vendor_name", nullable = false)
  private String vendorName;

  @Column(name = "vendor_email", length = 320, nullable = false)
  private String vendorEmail;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "questionnaire_id")
  private UUID questionnaireId;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "submitter_name")
  private String submitterName;

  @Column(name = "submitter_email", length = 320)
  private String submitterEmail;

  @Column(name = "portal_url", length = 1000)
  private String portalUrl;

  public State stateAt(Instant now) {
    if (submittedAt != null) return State.SUBMITTED;
    return now.isAfter(expiresAt) ? State.EXPIRED : State.PENDING;
  }

  public void markSubmitted(
      UUID questionnaireId,
      Instant at,
      String submitterName,
      String submitterEmail,
      String portalUrl) {
    this.questionnaireId = questionnaireId;
    this.submittedAt = at;
    this.submitterName = submitterName;
    this.submitterEmail = submitterEmail;
    this.portalUrl = portalUrl;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public String getVendorName() {
    return vendorName;
  }

  public void setVendorName(String vendorName) {
    this.vendorName = vendorName;
  }

  public String getVendorEmail() {
    return vendorEmail;
  }

  public void setVendorEmail(String vendorEmail) {
    this.vendorEmail = vendorEmail;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public UUID getQuestionnaireId() {
    return questionnaireId;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public String getSubmitterName() {
    return submitterName;
  }

  public String getSubmitterEmail() {
    return submitterEmail;
  }

  public String getPortalUrl() {
    return portalUrl;
  }
}
