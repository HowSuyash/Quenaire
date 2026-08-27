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

import com.complyai.config.StripeConfig;
import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.tenant.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

  private static final Logger log = LoggerFactory.getLogger(BillingService.class);

  private final StripeConfig stripeConfig;
  private final SubscriptionRepository subscriptionRepository;
  private final InvoiceRepository invoiceRepository;
  private final UsageRecordRepository usageRecordRepository;
  private final TenantRepository tenantRepository;
  private final ObjectMapper objectMapper;

  public BillingService(
      StripeConfig stripeConfig,
      SubscriptionRepository subscriptionRepository,
      InvoiceRepository invoiceRepository,
      UsageRecordRepository usageRecordRepository,
      TenantRepository tenantRepository,
      ObjectMapper objectMapper) {
    this.stripeConfig = stripeConfig;
    this.subscriptionRepository = subscriptionRepository;
    this.invoiceRepository = invoiceRepository;
    this.usageRecordRepository = usageRecordRepository;
    this.tenantRepository = tenantRepository;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void init() {
    if (stripeConfig.isConfigured()) {
      Stripe.apiKey = stripeConfig.getSecretKey();
      log.info("Stripe billing enabled");
      if (!stripeConfig.hasWebhookSecret()) {
        log.warn(
            "stripe.webhook-secret is not set - webhook events will be REJECTED. "
                + "Set it to the signing secret from the Stripe dashboard.");
      }
    } else {
      log.info("Stripe billing disabled (no secret key configured)");
    }
  }

  public boolean isConfigured() {
    return stripeConfig.isConfigured();
  }

  // ========== Subscription Management ==========

  @Transactional
  public Subscription createOrUpdateSubscription(
      Tenant tenant, BillingPlan plan, String paymentMethodId) {
    if (!isConfigured()) {
      throw new IllegalStateException("Stripe not configured");
    }

    Subscription subscription =
        subscriptionRepository
            .findByTenantIdAndDeletedAtIsNull(tenant.getId())
            .orElseGet(
                () -> {
                  Subscription sub = new Subscription();
                  sub.setTenant(tenant);
                  sub.setTenantId(tenant.getId());
                  return sub;
                });

    subscription.setPlan(plan);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodStart(OffsetDateTime.now());
    subscription.setCurrentPeriodEnd(OffsetDateTime.now().plusMonths(1));

    // In a real implementation, this would call Stripe API
    // For now, we simulate a successful subscription creation
    subscription.setStripeCustomerId("cus_simulated_" + tenant.getId());
    subscription.setStripeSubscriptionId("sub_simulated_" + System.currentTimeMillis());
    subscription.setStripePriceId(getStripePriceId(plan));

    return subscriptionRepository.save(subscription);
  }

  @Transactional
  public Subscription cancelSubscription(Tenant tenant, boolean atPeriodEnd) {
    Subscription subscription =
        subscriptionRepository
            .findByTenantIdAndDeletedAtIsNull(tenant.getId())
            .orElseThrow(() -> new BillingException("No subscription found for tenant"));

    if (atPeriodEnd) {
      subscription.setCancelAtPeriodEnd(true);
    } else {
      subscription.setStatus(SubscriptionStatus.CANCELED);
      subscription.setCanceledAt(OffsetDateTime.now());
    }
    return subscriptionRepository.save(subscription);
  }

  @Transactional
  public Subscription resumeSubscription(Tenant tenant) {
    Subscription subscription =
        subscriptionRepository
            .findByTenantIdAndDeletedAtIsNull(tenant.getId())
            .orElseThrow(() -> new BillingException("No subscription found for tenant"));

    subscription.setCancelAtPeriodEnd(false);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    return subscriptionRepository.save(subscription);
  }

  public Optional<Subscription> getSubscription(Tenant tenant) {
    return subscriptionRepository.findByTenantIdAndDeletedAtIsNull(tenant.getId());
  }

  // ========== Invoice Management ==========

  @Transactional
  public void syncInvoices(String stripeSubscriptionId) {
    if (!isConfigured()) return;
    // Simulated - in real implementation would sync from Stripe
    log.info("Syncing invoices for subscription: {}", stripeSubscriptionId);
  }

  // ========== Usage Tracking ==========

  @Transactional
  public void recordUsage(Tenant tenant, String metric, int quantity) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime periodStart =
        now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    OffsetDateTime periodEnd = periodStart.plusMonths(1).minusSeconds(1);

    UsageRecord record =
        usageRecordRepository
            .findByTenantIdAndMetricAndPeriodStartAndPeriodEndAndDeletedAtIsNull(
                tenant.getId(), metric, periodStart, periodEnd)
            .orElseGet(
                () -> {
                  UsageRecord ur = new UsageRecord();
                  ur.setTenant(tenant);
                  ur.setTenantId(tenant.getId());
                  ur.setMetric(metric);
                  ur.setPeriodStart(periodStart);
                  ur.setPeriodEnd(periodEnd);
                  return ur;
                });

    record.setQuantity(record.getQuantity() + quantity);
    usageRecordRepository.save(record);
  }

  public int getUsage(
      Tenant tenant, String metric, OffsetDateTime periodStart, OffsetDateTime periodEnd) {
    return usageRecordRepository
        .findByTenantIdAndMetricAndPeriodStartAndPeriodEndAndDeletedAtIsNull(
            tenant.getId(), metric, periodStart, periodEnd)
        .map(UsageRecord::getQuantity)
        .orElse(0);
  }

  // ========== Webhook Handling ==========

  /**
   * Verify and apply a Stripe webhook.
   *
   * <p>Signature verification is mandatory: without it anyone who can reach this endpoint could
   * grant themselves an Enterprise subscription by POSTing a fabricated {@code
   * customer.subscription.updated}. An unset webhook secret therefore rejects rather than trusts.
   */
  @Transactional
  public void handleWebhookEvent(String payload, String signatureHeader) {
    if (!stripeConfig.hasWebhookSecret()) {
      throw new BillingException("Webhook secret is not configured; refusing unverified event");
    }

    Event event;
    try {
      event = Webhook.constructEvent(payload, signatureHeader, stripeConfig.getWebhookSecret());
    } catch (SignatureVerificationException e) {
      log.warn("Rejected Stripe webhook with invalid signature");
      throw new BillingException("Invalid webhook signature", e);
    }

    log.info("Processing Stripe webhook {} ({})", event.getType(), event.getId());

    JsonNode object = extractObject(payload);
    if (object == null) {
      log.warn("Stripe webhook {} had no data.object", event.getId());
      return;
    }

    switch (event.getType()) {
      case "customer.subscription.created",
          "customer.subscription.updated",
          "customer.subscription.resumed" ->
          applySubscriptionUpdate(object);
      case "customer.subscription.deleted" -> applySubscriptionDeleted(object);
      case "invoice.paid", "invoice.payment_succeeded" -> applyInvoice(object, InvoiceStatus.PAID);
      case "invoice.payment_failed" -> applyInvoicePaymentFailed(object);
      case "invoice.created", "invoice.finalized" -> applyInvoice(object, InvoiceStatus.OPEN);
      default -> log.debug("Ignoring unhandled Stripe event type {}", event.getType());
    }
  }

  private JsonNode extractObject(String payload) {
    try {
      JsonNode node = objectMapper.readTree(payload).path("data").path("object");
      return node.isMissingNode() || node.isNull() ? null : node;
    } catch (Exception e) {
      log.warn("Could not parse Stripe webhook payload", e);
      return null;
    }
  }

  private void applySubscriptionUpdate(JsonNode object) {
    String stripeSubscriptionId = object.path("id").asText(null);
    if (stripeSubscriptionId == null) return;

    Optional<Subscription> found =
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);
    if (found.isEmpty()) {
      // Arrives when Checkout completes before our record exists; the
      // customer id lets us adopt it rather than dropping the event.
      found = subscriptionRepository.findByStripeCustomerId(object.path("customer").asText(null));
      found.ifPresent(sub -> sub.setStripeSubscriptionId(stripeSubscriptionId));
    }

    if (found.isEmpty()) {
      log.warn("No local subscription for Stripe subscription {}", stripeSubscriptionId);
      return;
    }

    Subscription subscription = found.get();
    subscription.setStatus(mapSubscriptionStatus(object.path("status").asText(null)));
    subscription.setCancelAtPeriodEnd(object.path("cancel_at_period_end").asBoolean(false));

    toOffsetDateTime(object.path("current_period_start"))
        .ifPresent(subscription::setCurrentPeriodStart);
    toOffsetDateTime(object.path("current_period_end"))
        .ifPresent(subscription::setCurrentPeriodEnd);
    toOffsetDateTime(object.path("trial_start")).ifPresent(subscription::setTrialStart);
    toOffsetDateTime(object.path("trial_end")).ifPresent(subscription::setTrialEnd);
    toOffsetDateTime(object.path("canceled_at")).ifPresent(subscription::setCanceledAt);

    JsonNode price = object.path("items").path("data").path(0).path("price");
    String priceId = price.path("id").asText(null);
    if (priceId != null) {
      subscription.setStripePriceId(priceId);
      planForPriceId(priceId).ifPresent(subscription::setPlan);
    }

    subscriptionRepository.save(subscription);
    syncTenantPlan(subscription);
  }

  private void applySubscriptionDeleted(JsonNode object) {
    String stripeSubscriptionId = object.path("id").asText(null);
    if (stripeSubscriptionId == null) return;

    subscriptionRepository
        .findByStripeSubscriptionId(stripeSubscriptionId)
        .ifPresent(
            subscription -> {
              subscription.setStatus(SubscriptionStatus.CANCELED);
              subscription.setCanceledAt(OffsetDateTime.now());
              subscription.setPlan(BillingPlan.FREE);
              subscriptionRepository.save(subscription);
              syncTenantPlan(subscription);
            });
  }

  private void applyInvoice(JsonNode object, InvoiceStatus status) {
    String stripeInvoiceId = object.path("id").asText(null);
    if (stripeInvoiceId == null) return;

    String stripeSubscriptionId = object.path("subscription").asText(null);
    Optional<Subscription> subscription =
        stripeSubscriptionId == null
            ? Optional.empty()
            : subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);

    if (subscription.isEmpty()) {
      log.debug("Invoice {} has no matching local subscription", stripeInvoiceId);
      return;
    }

    Invoice invoice =
        invoiceRepository
            .findByStripeInvoiceId(stripeInvoiceId)
            .orElseGet(
                () -> {
                  Invoice created = new Invoice();
                  created.setStripeInvoiceId(stripeInvoiceId);
                  created.setTenant(subscription.get().getTenant());
                  created.setTenantId(subscription.get().getTenantId());
                  return created;
                });

    invoice.setStripeSubscriptionId(stripeSubscriptionId);
    invoice.setInvoiceNumber(object.path("number").asText(null));
    invoice.setStatus(status);
    invoice.setAmountDue(object.path("amount_due").asLong(0));
    invoice.setAmountPaid(object.path("amount_paid").asLong(0));
    invoice.setAmountRemaining(object.path("amount_remaining").asLong(0));
    invoice.setCurrency(object.path("currency").asText("usd"));
    invoice.setInvoicePdfUrl(object.path("invoice_pdf").asText(null));
    invoice.setHostedInvoiceUrl(object.path("hosted_invoice_url").asText(null));

    toOffsetDateTime(object.path("due_date")).ifPresent(invoice::setDueDate);
    toOffsetDateTime(object.path("period_start")).ifPresent(invoice::setPeriodStart);
    toOffsetDateTime(object.path("period_end")).ifPresent(invoice::setPeriodEnd);
    if (status == InvoiceStatus.PAID) {
      invoice.setPaidAt(OffsetDateTime.now());
    }

    invoiceRepository.save(invoice);
  }

  private void applyInvoicePaymentFailed(JsonNode object) {
    applyInvoice(object, InvoiceStatus.OPEN);

    String stripeSubscriptionId = object.path("subscription").asText(null);
    if (stripeSubscriptionId == null) return;

    subscriptionRepository
        .findByStripeSubscriptionId(stripeSubscriptionId)
        .ifPresent(
            subscription -> {
              subscription.setStatus(SubscriptionStatus.PAST_DUE);
              subscriptionRepository.save(subscription);
              syncTenantPlan(subscription);
            });
  }

  /** Keep the denormalised tenant plan in step with the subscription. */
  private void syncTenantPlan(Subscription subscription) {
    if (subscription.getTenantId() == null) return;

    tenantRepository
        .findById(subscription.getTenantId())
        .ifPresent(
            tenant -> {
              boolean entitled =
                  subscription.getStatus() == SubscriptionStatus.ACTIVE
                      || subscription.getStatus() == SubscriptionStatus.TRIALING;

              tenant.setPlan(
                  entitled
                      ? com.complyai.domain.tenant.TenantPlan.valueOf(subscription.getPlan().name())
                      : com.complyai.domain.tenant.TenantPlan.FREE);
              // Tenant stores Instant; Subscription stores OffsetDateTime.
              tenant.setPlanExpiresAt(
                  subscription.getCurrentPeriodEnd() != null
                      ? subscription.getCurrentPeriodEnd().toInstant()
                      : null);
              tenantRepository.save(tenant);
            });
  }

  private Optional<BillingPlan> planForPriceId(String priceId) {
    if (priceId == null) return Optional.empty();
    if (priceId.equals(stripeConfig.getStarterPriceId())) return Optional.of(BillingPlan.STARTER);
    if (priceId.equals(stripeConfig.getProfessionalPriceId()))
      return Optional.of(BillingPlan.PROFESSIONAL);
    if (priceId.equals(stripeConfig.getEnterprisePriceId()))
      return Optional.of(BillingPlan.ENTERPRISE);
    if (priceId.equals(stripeConfig.getCustomPriceId())) return Optional.of(BillingPlan.CUSTOM);
    return Optional.empty();
  }

  private static SubscriptionStatus mapSubscriptionStatus(String stripeStatus) {
    if (stripeStatus == null) return SubscriptionStatus.INCOMPLETE;
    return switch (stripeStatus) {
      case "active" -> SubscriptionStatus.ACTIVE;
      case "trialing" -> SubscriptionStatus.TRIALING;
      case "past_due", "unpaid" -> SubscriptionStatus.PAST_DUE;
      case "canceled" -> SubscriptionStatus.CANCELED;
      case "paused" -> SubscriptionStatus.PAUSED;
      default -> SubscriptionStatus.INCOMPLETE;
    };
  }

  /** Stripe sends epoch seconds; absent fields arrive as 0 or null. */
  private static Optional<OffsetDateTime> toOffsetDateTime(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) return Optional.empty();
    long epochSeconds = node.asLong(0);
    if (epochSeconds <= 0) return Optional.empty();
    return Optional.of(
        OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochSecond(epochSeconds), java.time.ZoneOffset.UTC));
  }

  // ========== Helpers ==========

  private String getStripePriceId(BillingPlan plan) {
    String priceId =
        switch (plan) {
          case STARTER -> stripeConfig.getStarterPriceId();
          case PROFESSIONAL -> stripeConfig.getProfessionalPriceId();
          case ENTERPRISE -> stripeConfig.getEnterprisePriceId();
          case CUSTOM -> stripeConfig.getCustomPriceId();
          default -> null;
        };

    if (plan != BillingPlan.FREE && (priceId == null || priceId.isBlank())) {
      throw new BillingException(
          "No Stripe price configured for plan "
              + plan
              + " (set stripe.price."
              + plan.name().toLowerCase()
              + ")");
    }
    return priceId;
  }

  // ========== Plan Limits ==========

  public boolean checkLimit(Tenant tenant, String metric, int requested) {
    Optional<Subscription> subOpt = getSubscription(tenant);
    if (subOpt.isEmpty()) {
      return switch (metric) {
        case "questionnaires" -> requested <= 5;
        case "documents" -> requested <= 100;
        case "questions" -> requested <= 1000;
        default -> true;
      };
    }

    Subscription sub = subOpt.get();
    BillingPlan plan = sub.getPlan();

    return switch (metric) {
      case "questionnaires" ->
          plan.isUnlimited("questionnaires") || requested <= plan.getMaxQuestionnairesPerMonth();
      case "documents" -> plan.isUnlimited("documents") || requested <= plan.getMaxDocuments();
      case "questions" ->
          plan.isUnlimited("questions") || requested <= plan.getMaxQuestionsPerQuestionnaire();
      default -> true;
    };
  }

  public BillingPlan getCurrentPlan(Tenant tenant) {
    return getSubscription(tenant).map(Subscription::getPlan).orElse(BillingPlan.FREE);
  }

  public InvoiceRepository getInvoiceRepository() {
    return invoiceRepository;
  }

  public StripeConfig getStripeConfig() {
    return stripeConfig;
  }

  public static class BillingException extends RuntimeException {
    public BillingException(String message) {
      super(message);
    }

    public BillingException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
