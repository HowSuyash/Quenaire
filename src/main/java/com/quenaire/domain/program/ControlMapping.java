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
import java.util.UUID;

/**
 * Links an observed {@link ProgramSignal} to a control in a compliance framework, so "MFA enforced
 * on all IAM users" can answer both SOC 2 CC6.1 and ISO 27001 A.5.17 without duplicating the
 * evidence.
 */
@Entity
@Table(
    name = "control_mappings",
    indexes = {
      @Index(name = "idx_control_mapping_tenant", columnList = "tenant_id"),
      @Index(name = "idx_control_mapping_signal", columnList = "signal_id"),
      @Index(name = "idx_control_mapping_framework", columnList = "framework"),
      @Index(name = "idx_control_mapping_control", columnList = "control_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_control_mapping",
          columnNames = {"tenant_id", "signal_id", "framework", "control_id"})
    })
public class ControlMapping extends BaseEntity {

  public enum Framework {
    SOC2,
    ISO_27001,
    NIST_CSF,
    PCI_DSS,
    HIPAA,
    GDPR,
    CIS
  }

  @Column(name = "signal_id", nullable = false)
  private UUID signalId;

  @Enumerated(EnumType.STRING)
  @Column(name = "framework", length = 32, nullable = false)
  private Framework framework;

  /** Control identifier within the framework, e.g. "CC6.1" or "A.5.17". */
  @Column(name = "control_id", length = 64, nullable = false)
  private String controlId;

  @Column(name = "control_title", length = 500)
  private String controlTitle;

  /**
   * How strongly this signal supports the control, 0-100. Mappings inferred from keywords score
   * lower than ones an operator confirmed.
   */
  @Column(name = "strength", nullable = false)
  private Integer strength = 50;

  @Column(name = "confirmed_by_id")
  private UUID confirmedById;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
  private Tenant tenant;

  // This field writes tenant_id; the association above only reads it, so setTenantId persists.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  public boolean isConfirmed() {
    return confirmedById != null;
  }

  public UUID getSignalId() {
    return signalId;
  }

  public void setSignalId(UUID signalId) {
    this.signalId = signalId;
  }

  public Framework getFramework() {
    return framework;
  }

  public void setFramework(Framework framework) {
    this.framework = framework;
  }

  public String getControlId() {
    return controlId;
  }

  public void setControlId(String controlId) {
    this.controlId = controlId;
  }

  public String getControlTitle() {
    return controlTitle;
  }

  public void setControlTitle(String controlTitle) {
    this.controlTitle = controlTitle;
  }

  public Integer getStrength() {
    return strength;
  }

  public void setStrength(Integer strength) {
    this.strength = strength;
  }

  public UUID getConfirmedById() {
    return confirmedById;
  }

  public void setConfirmedById(UUID confirmedById) {
    this.confirmedById = confirmedById;
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
