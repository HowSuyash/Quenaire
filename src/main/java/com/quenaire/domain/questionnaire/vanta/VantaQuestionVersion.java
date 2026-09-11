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
package com.quenaire.domain.questionnaire.vanta;

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One recorded change to a questionnaire answer.
 *
 * <p>Who changed it, when, and what it said before. This is the evidence behind an answer rather
 * than housekeeping: vendor questionnaires ask whether changes to security documentation are
 * logged, and a customer defending an answer months later needs to know who wrote it.
 *
 * <p>It was previously kept as a JSON array in the question's own {@code metadata_json} column,
 * which overwrote whatever else that column held, and nothing ever read it back.
 */
@Entity
@Table(name = "vanta_question_versions")
public class VantaQuestionVersion extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "question_id", nullable = false)
  private UUID questionId;

  @Column(name = "action", nullable = false, length = 50)
  private String action;

  /** Null when the change came from the system rather than a person. */
  @Column(name = "actor_id")
  private UUID actorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status", columnDefinition = "vanta_question_status")
  private VantaQuestion.QuestionStatus previousStatus;

  @Column(name = "previous_answer", columnDefinition = "text")
  private String previousAnswer;

  @Column(name = "new_answer", columnDefinition = "text")
  private String newAnswer;

  @Column(name = "note", columnDefinition = "text")
  private String note;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  public UUID getQuestionId() {
    return questionId;
  }

  public void setQuestionId(UUID questionId) {
    this.questionId = questionId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public UUID getActorId() {
    return actorId;
  }

  public void setActorId(UUID actorId) {
    this.actorId = actorId;
  }

  public VantaQuestion.QuestionStatus getPreviousStatus() {
    return previousStatus;
  }

  public void setPreviousStatus(VantaQuestion.QuestionStatus previousStatus) {
    this.previousStatus = previousStatus;
  }

  public String getPreviousAnswer() {
    return previousAnswer;
  }

  public void setPreviousAnswer(String previousAnswer) {
    this.previousAnswer = previousAnswer;
  }

  public String getNewAnswer() {
    return newAnswer;
  }

  public void setNewAnswer(String newAnswer) {
    this.newAnswer = newAnswer;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }
}
