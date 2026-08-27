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
package com.complyai.domain.audit;

import com.complyai.domain.common.BaseEntity;
import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "audit_logs",
    indexes = {
      @Index(name = "idx_audit_tenant_id", columnList = "tenant_id"),
      @Index(name = "idx_audit_user_id", columnList = "user_id"),
      @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
      @Index(name = "idx_audit_action", columnList = "action"),
      @Index(name = "idx_audit_created_at", columnList = "created_at")
    })
public class AuditLog extends BaseEntity {

  @NotBlank
  @Size(max = 100)
  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id")
  private UUID entityId;

  @NotBlank
  @Size(max = 50)
  @Column(name = "action", nullable = false)
  private String action;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false)
  private AuditSeverity severity = AuditSeverity.INFO;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Lob
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "old_values_json", columnDefinition = "jsonb")
  private String oldValuesJson;

  @Lob
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "new_values_json", columnDefinition = "jsonb")
  private String newValuesJson;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Size(max = 500)
  @Column(name = "user_agent")
  private String userAgent;

  @Column(name = "request_id", length = 36)
  private String requestId;

  @Column(name = "session_id", length = 36)
  private String sessionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson = "{}";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public void setEntityId(UUID entityId) {
    this.entityId = entityId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public AuditSeverity getSeverity() {
    return severity;
  }

  public void setSeverity(AuditSeverity severity) {
    this.severity = severity;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getOldValuesJson() {
    return oldValuesJson;
  }

  public void setOldValuesJson(String oldValuesJson) {
    this.oldValuesJson = oldValuesJson;
  }

  public String getNewValuesJson() {
    return newValuesJson;
  }

  public void setNewValuesJson(String newValuesJson) {
    this.newValuesJson = newValuesJson;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }

  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }
}
