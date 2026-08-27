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
package com.complyai.domain.billing;

import com.complyai.domain.common.BaseEntity;
import com.complyai.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "invoices",
    indexes = {
      @Index(name = "idx_invoices_tenant_id", columnList = "tenant_id"),
      @Index(name = "idx_invoices_stripe_invoice_id", columnList = "stripe_invoice_id"),
      @Index(name = "idx_invoices_status", columnList = "status"),
      @Index(name = "idx_invoices_due_date", columnList = "due_date")
    })
public class Invoice extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "tenant_id", insertable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "stripe_invoice_id", unique = true)
  private String stripeInvoiceId;

  @Column(name = "stripe_subscription_id")
  private String stripeSubscriptionId;

  @Column(name = "invoice_number", nullable = false, unique = true)
  private String invoiceNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private InvoiceStatus status = InvoiceStatus.DRAFT;

  // Money in minor units (cents/paise). BIGINT because int overflows for
  // currencies with small units -- an IDR invoice can exceed 2^31.
  @Column(name = "amount_due", nullable = false)
  private Long amountDue;

  @Column(name = "amount_paid")
  private Long amountPaid;

  @Column(name = "amount_remaining")
  private Long amountRemaining;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency = "usd";

  @Column(name = "due_date")
  private OffsetDateTime dueDate;

  @Column(name = "paid_at")
  private OffsetDateTime paidAt;

  @Column(name = "period_start")
  private OffsetDateTime periodStart;

  @Column(name = "period_end")
  private OffsetDateTime periodEnd;

  @Column(name = "invoice_pdf_url")
  private String invoicePdfUrl;

  @Column(name = "hosted_invoice_url")
  private String hostedInvoiceUrl;

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

  public String getStripeInvoiceId() {
    return stripeInvoiceId;
  }

  public void setStripeInvoiceId(String stripeInvoiceId) {
    this.stripeInvoiceId = stripeInvoiceId;
  }

  public String getStripeSubscriptionId() {
    return stripeSubscriptionId;
  }

  public void setStripeSubscriptionId(String stripeSubscriptionId) {
    this.stripeSubscriptionId = stripeSubscriptionId;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public void setInvoiceNumber(String invoiceNumber) {
    this.invoiceNumber = invoiceNumber;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public void setStatus(InvoiceStatus status) {
    this.status = status;
  }

  public Long getAmountDue() {
    return amountDue;
  }

  public void setAmountDue(Long amountDue) {
    this.amountDue = amountDue;
  }

  public Long getAmountPaid() {
    return amountPaid;
  }

  public void setAmountPaid(Long amountPaid) {
    this.amountPaid = amountPaid;
  }

  public Long getAmountRemaining() {
    return amountRemaining;
  }

  public void setAmountRemaining(Long amountRemaining) {
    this.amountRemaining = amountRemaining;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public void setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
  }

  public OffsetDateTime getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(OffsetDateTime paidAt) {
    this.paidAt = paidAt;
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

  public String getInvoicePdfUrl() {
    return invoicePdfUrl;
  }

  public void setInvoicePdfUrl(String invoicePdfUrl) {
    this.invoicePdfUrl = invoicePdfUrl;
  }

  public String getHostedInvoiceUrl() {
    return hostedInvoiceUrl;
  }

  public void setHostedInvoiceUrl(String hostedInvoiceUrl) {
    this.hostedInvoiceUrl = hostedInvoiceUrl;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }
}
