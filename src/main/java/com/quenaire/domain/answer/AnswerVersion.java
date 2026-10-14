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
package com.quenaire.domain.answer;

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "answer_versions",
    indexes = {
      @Index(name = "idx_answer_version_answer", columnList = "answer_id"),
      @Index(name = "idx_answer_version_tenant", columnList = "tenant_id")
    })
public class AnswerVersion extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "answer_id", nullable = false)
  private Answer answer;

  @Column(name = "answer_id", insertable = false, updatable = false)
  private UUID answerId;

  @Column(name = "version_number", nullable = false)
  private Integer versionNumber;

  @Column(name = "content", columnDefinition = "text")
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "citations_json", columnDefinition = "jsonb")
  private String citationsJson = "[]";

  @Column(name = "confidence_score")
  private Double confidenceScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AnswerStatus status;

  @Column(name = "review_notes", columnDefinition = "text")
  private String reviewNotes;

  @Column(name = "created_by_id")
  private UUID createdById;

  @Column(name = "change_summary", columnDefinition = "text")
  private String changeSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
  private Tenant tenant;

  // This field writes tenant_id; the association above only reads it, so setTenantId persists.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  public Answer getAnswer() {
    return answer;
  }

  public void setAnswer(Answer answer) {
    this.answer = answer;
  }

  public UUID getAnswerId() {
    return answerId;
  }

  public void setAnswerId(UUID answerId) {
    this.answerId = answerId;
  }

  public Integer getVersionNumber() {
    return versionNumber;
  }

  public void setVersionNumber(Integer versionNumber) {
    this.versionNumber = versionNumber;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getCitationsJson() {
    return citationsJson;
  }

  public void setCitationsJson(String citationsJson) {
    this.citationsJson = citationsJson;
  }

  public Double getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Double confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public AnswerStatus getStatus() {
    return status;
  }

  public void setStatus(AnswerStatus status) {
    this.status = status;
  }

  public String getReviewNotes() {
    return reviewNotes;
  }

  public void setReviewNotes(String reviewNotes) {
    this.reviewNotes = reviewNotes;
  }

  public UUID getCreatedById() {
    return createdById;
  }

  public void setCreatedById(UUID createdById) {
    this.createdById = createdById;
  }

  public String getChangeSummary() {
    return changeSummary;
  }

  public void setChangeSummary(String changeSummary) {
    this.changeSummary = changeSummary;
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
