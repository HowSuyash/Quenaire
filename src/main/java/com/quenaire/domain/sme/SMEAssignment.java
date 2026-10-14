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
import java.util.UUID;

/**
 * One question routed to one SME, with its own SLA clock.
 *
 * <p>This is the durable backing store for the review queue — the previous implementation kept
 * these in a {@code ConcurrentHashMap} keyed by question id, which lost every assignment on restart
 * and could only hold one assignment per question across all tenants.
 */
@Entity
@Table(
    name = "sme_assignments",
    indexes = {
      @Index(name = "idx_sme_assignment_tenant", columnList = "tenant_id"),
      @Index(name = "idx_sme_assignment_question", columnList = "question_id"),
      @Index(name = "idx_sme_assignment_questionnaire", columnList = "questionnaire_id"),
      @Index(name = "idx_sme_assignment_sme", columnList = "sme_user_id"),
      @Index(name = "idx_sme_assignment_status", columnList = "status"),
      @Index(name = "idx_sme_assignment_due", columnList = "due_at")
    })
public class SMEAssignment extends BaseEntity {

  @Column(name = "question_id", nullable = false)
  private UUID questionId;

  @Column(name = "questionnaire_id", nullable = false)
  private UUID questionnaireId;

  @Column(name = "sme_user_id", nullable = false)
  private UUID smeUserId;

  @Column(name = "assigned_by_id")
  private UUID assignedById;

  @Column(name = "category", length = 255)
  private String category;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private SMEAssignmentStatus status = SMEAssignmentStatus.PENDING;

  @Column(name = "assigned_at", nullable = false)
  private OffsetDateTime assignedAt = OffsetDateTime.now();

  @Column(name = "due_at")
  private OffsetDateTime dueAt;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
  private Tenant tenant;

  // This field writes tenant_id; the association above only reads it, so setTenantId persists.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  /** True while the SLA clock has run out and the item is still open. */
  public boolean isOverdue() {
    return dueAt != null
        && dueAt.isBefore(OffsetDateTime.now())
        && (status == SMEAssignmentStatus.PENDING || status == SMEAssignmentStatus.IN_PROGRESS);
  }

  public boolean isOpen() {
    return status == SMEAssignmentStatus.PENDING || status == SMEAssignmentStatus.IN_PROGRESS;
  }

  public UUID getQuestionId() {
    return questionId;
  }

  public void setQuestionId(UUID questionId) {
    this.questionId = questionId;
  }

  public UUID getQuestionnaireId() {
    return questionnaireId;
  }

  public void setQuestionnaireId(UUID questionnaireId) {
    this.questionnaireId = questionnaireId;
  }

  public UUID getSmeUserId() {
    return smeUserId;
  }

  public void setSmeUserId(UUID smeUserId) {
    this.smeUserId = smeUserId;
  }

  public UUID getAssignedById() {
    return assignedById;
  }

  public void setAssignedById(UUID assignedById) {
    this.assignedById = assignedById;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public SMEAssignmentStatus getStatus() {
    return status;
  }

  public void setStatus(SMEAssignmentStatus status) {
    this.status = status;
  }

  public OffsetDateTime getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(OffsetDateTime assignedAt) {
    this.assignedAt = assignedAt;
  }

  public OffsetDateTime getDueAt() {
    return dueAt;
  }

  public void setDueAt(OffsetDateTime dueAt) {
    this.dueAt = dueAt;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(OffsetDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(OffsetDateTime completedAt) {
    this.completedAt = completedAt;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
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
