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
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One published row on a Trust Center page: a certification, a downloadable document, a control
 * highlight, or a pre-answered FAQ.
 */
@Entity
@Table(
    name = "trust_center_items",
    indexes = {
      @Index(name = "idx_trust_item_profile", columnList = "profile_id"),
      @Index(name = "idx_trust_item_kind", columnList = "kind")
    })
public class TrustCenterItem extends BaseEntity {

  public enum Kind {
    /** SOC 2, ISO 27001, PCI… — rendered as a badge with validity dates. */
    CERTIFICATION,
    /** A document a prospect can request or download. */
    DOCUMENT,
    /** A short control statement, e.g. "AES-256 at rest". */
    CONTROL,
    /** A question/answer pair promoted from the answer library. */
    FAQ
  }

  /** Whether a visitor gets the file directly or has to ask for it. */
  public enum Access {
    PUBLIC,
    REQUEST_ONLY
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "profile_id", nullable = false)
  private TrustCenterProfile profile;

  @Column(name = "profile_id", insertable = false, updatable = false)
  private UUID profileId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 32, nullable = false)
  private Kind kind = Kind.CONTROL;

  @Column(name = "title", length = 500, nullable = false)
  private String title;

  @Column(name = "body", columnDefinition = "TEXT")
  private String body;

  @Column(name = "icon", length = 64)
  private String icon;

  @Column(name = "issuer", length = 255)
  private String issuer;

  @Column(name = "valid_from")
  private LocalDate validFrom;

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Enumerated(EnumType.STRING)
  @Column(name = "access_level", length = 32, nullable = false)
  private Access accessLevel = Access.PUBLIC;

  /** Source document, when this row was published from the library. */
  @Column(name = "document_id")
  private UUID documentId;

  @Column(name = "external_url", length = 1000)
  private String externalUrl;

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder = 0;

  public TrustCenterProfile getProfile() {
    return profile;
  }

  public void setProfile(TrustCenterProfile profile) {
    this.profile = profile;
  }

  public UUID getProfileId() {
    return profileId;
  }

  public void setProfileId(UUID profileId) {
    this.profileId = profileId;
  }

  public Kind getKind() {
    return kind;
  }

  public void setKind(Kind kind) {
    this.kind = kind;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(LocalDate validFrom) {
    this.validFrom = validFrom;
  }

  public LocalDate getValidUntil() {
    return validUntil;
  }

  public void setValidUntil(LocalDate validUntil) {
    this.validUntil = validUntil;
  }

  public Access getAccessLevel() {
    return accessLevel;
  }

  public void setAccessLevel(Access accessLevel) {
    this.accessLevel = accessLevel;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public void setDocumentId(UUID documentId) {
    this.documentId = documentId;
  }

  public String getExternalUrl() {
    return externalUrl;
  }

  public void setExternalUrl(String externalUrl) {
    this.externalUrl = externalUrl;
  }

  public Integer getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(Integer displayOrder) {
    this.displayOrder = displayOrder;
  }
}
