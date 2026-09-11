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
package com.quenaire.domain.agent;

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "agent_tasks",
    indexes = {
      @Index(name = "idx_agent_task_tenant", columnList = "tenant_id"),
      @Index(name = "idx_agent_task_status", columnList = "status"),
      @Index(name = "idx_agent_task_questionnaire", columnList = "questionnaire_id"),
      @Index(name = "idx_agent_task_priority", columnList = "priority")
    })
public class AgentTask extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "task_type", nullable = false)
  private TaskType taskType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TaskStatus status = TaskStatus.PENDING;

  @Column(name = "priority", nullable = false)
  private Integer priority = 0;

  @Column(name = "questionnaire_id")
  private UUID questionnaireId;

  @Column(name = "question_id")
  private UUID questionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", columnDefinition = "jsonb")
  private String payloadJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_json", columnDefinition = "jsonb")
  private String resultJson;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "retry_count")
  private Integer retryCount = 0;

  @Column(name = "max_retries")
  private Integer maxRetries = 3;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "next_retry_at")
  private OffsetDateTime nextRetryAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "tenant_id", insertable = false, updatable = false)
  private UUID tenantId;

  public enum TaskType {
    PROCESS_QUESTIONNAIRE,
    GENERATE_ANSWER,
    ROUTE_TO_SME,
    SEND_NOTIFICATION,
    EXPORT_QUESTIONNAIRE,
    SYNC_KNOWLEDGE_BASE,
    INGEST_DOCUMENT
  }

  public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING,
    CANCELLED
  }

  // Getters and Setters
  public TaskType getTaskType() {
    return taskType;
  }

  public void setTaskType(TaskType taskType) {
    this.taskType = taskType;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public void setStatus(TaskStatus status) {
    this.status = status;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public UUID getQuestionnaireId() {
    return questionnaireId;
  }

  public void setQuestionnaireId(UUID questionnaireId) {
    this.questionnaireId = questionnaireId;
  }

  public UUID getQuestionId() {
    return questionId;
  }

  public void setQuestionId(UUID questionId) {
    this.questionId = questionId;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String resultJson) {
    this.resultJson = resultJson;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public Integer getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(Integer maxRetries) {
    this.maxRetries = maxRetries;
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

  public OffsetDateTime getNextRetryAt() {
    return nextRetryAt;
  }

  public void setNextRetryAt(OffsetDateTime nextRetryAt) {
    this.nextRetryAt = nextRetryAt;
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
