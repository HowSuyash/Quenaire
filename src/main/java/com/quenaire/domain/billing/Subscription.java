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
    name = "subscriptions",
    indexes = {
      @Index(name = "idx_subscriptions_tenant_id", columnList = "tenant_id"),
      @Index(name = "idx_subscriptions_stripe_customer_id", columnList = "stripe_customer_id"),
      @Index(
          name = "idx_subscriptions_stripe_subscription_id",
          columnList = "stripe_subscription_id"),
      @Index(name = "idx_subscriptions_status", columnList = "status")
    })
public class Subscription extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "tenant_id", insertable = false, updatable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "plan", nullable = false)
  private BillingPlan plan = BillingPlan.FREE;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SubscriptionStatus status = SubscriptionStatus.INCOMPLETE;

  @Column(name = "stripe_customer_id")
  private String stripeCustomerId;

  @Column(name = "stripe_subscription_id")
  private String stripeSubscriptionId;

  @Column(name = "stripe_price_id")
  private String stripePriceId;

  @Column(name = "current_period_start")
  private OffsetDateTime currentPeriodStart;

  @Column(name = "current_period_end")
  private OffsetDateTime currentPeriodEnd;

  @Column(name = "cancel_at_period_end")
  private Boolean cancelAtPeriodEnd = false;

  @Column(name = "canceled_at")
  private OffsetDateTime canceledAt;

  @Column(name = "trial_start")
  private OffsetDateTime trialStart;

  @Column(name = "trial_end")
  private OffsetDateTime trialEnd;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson = "{}";

  // Getters and setters
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

  public BillingPlan getPlan() {
    return plan;
  }

  public void setPlan(BillingPlan plan) {
    this.plan = plan;
  }

  public SubscriptionStatus getStatus() {
    return status;
  }

  public void setStatus(SubscriptionStatus status) {
    this.status = status;
  }

  public String getStripeCustomerId() {
    return stripeCustomerId;
  }

  public void setStripeCustomerId(String stripeCustomerId) {
    this.stripeCustomerId = stripeCustomerId;
  }

  public String getStripeSubscriptionId() {
    return stripeSubscriptionId;
  }

  public void setStripeSubscriptionId(String stripeSubscriptionId) {
    this.stripeSubscriptionId = stripeSubscriptionId;
  }

  public String getStripePriceId() {
    return stripePriceId;
  }

  public void setStripePriceId(String stripePriceId) {
    this.stripePriceId = stripePriceId;
  }

  public OffsetDateTime getCurrentPeriodStart() {
    return currentPeriodStart;
  }

  public void setCurrentPeriodStart(OffsetDateTime currentPeriodStart) {
    this.currentPeriodStart = currentPeriodStart;
  }

  public OffsetDateTime getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public void setCurrentPeriodEnd(OffsetDateTime currentPeriodEnd) {
    this.currentPeriodEnd = currentPeriodEnd;
  }

  public Boolean getCancelAtPeriodEnd() {
    return cancelAtPeriodEnd;
  }

  public void setCancelAtPeriodEnd(Boolean cancelAtPeriodEnd) {
    this.cancelAtPeriodEnd = cancelAtPeriodEnd;
  }

  public OffsetDateTime getCanceledAt() {
    return canceledAt;
  }

  public void setCanceledAt(OffsetDateTime canceledAt) {
    this.canceledAt = canceledAt;
  }

  public OffsetDateTime getTrialStart() {
    return trialStart;
  }

  public void setTrialStart(OffsetDateTime trialStart) {
    this.trialStart = trialStart;
  }

  public OffsetDateTime getTrialEnd() {
    return trialEnd;
  }

  public void setTrialEnd(OffsetDateTime trialEnd) {
    this.trialEnd = trialEnd;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }
}
