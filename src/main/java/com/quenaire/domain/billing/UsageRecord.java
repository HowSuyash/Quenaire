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
package com.quenaire.domain.billing;

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "usage_records",
    indexes = {
      @Index(name = "idx_usage_tenant_period", columnList = "tenant_id, period_start, period_end"),
      @Index(name = "idx_usage_metric", columnList = "metric")
    })
public class UsageRecord extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
  private Tenant tenant;

  // This field writes tenant_id; the association above only reads it, so setTenantId persists.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "metric", nullable = false)
  private String metric;

  @Column(name = "quantity", nullable = false)
  private Integer quantity = 0;

  @Column(name = "period_start", nullable = false)
  private OffsetDateTime periodStart;

  @Column(name = "period_end", nullable = false)
  private OffsetDateTime periodEnd;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson = "{}";

  // Getters and setters
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

  public String getMetric() {
    return metric;
  }

  public void setMetric(String metric) {
    this.metric = metric;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  public OffsetDateTime getPeriodStart() {
    return periodStart;
  }

  public void setPeriodStart(OffsetDateTime periodStart) {
    this.periodStart = periodStart;
  }

  public OffsetDateTime getPeriodEnd() {
    return periodEnd;
  }

  public void setPeriodEnd(OffsetDateTime periodEnd) {
    this.periodEnd = periodEnd;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }
}
