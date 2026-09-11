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

import com.quenaire.domain.billing.BillingService.BillingException;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.security.TenantContext;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
public class BillingController {

  private final BillingService billingService;

  public BillingController(BillingService billingService) {
    this.billingService = billingService;
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getBillingStatus() {
    Tenant tenant = getCurrentTenant();
    Optional<Subscription> subscription = billingService.getSubscription(tenant);
    BillingPlan plan = billingService.getCurrentPlan(tenant);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("configured", billingService.isConfigured());
    response.put("plan", plan.name());
    response.put("planName", plan.getName());
    response.put("monthlyPrice", plan.getMonthlyPriceUsd());
    response.put("annualPrice", plan.getAnnualPriceUsd());
    subscription.ifPresent(s -> response.put("subscription", mapSubscription(s)));
    response.put(
        "limits",
        Map.of(
            "questionnaires", plan.getMaxQuestionnairesPerMonth(),
            "documents", plan.getMaxDocuments(),
            "questions", plan.getMaxQuestionsPerQuestionnaire(),
            "apiAccess", plan.isApiAccess(),
            "integrations", plan.isIntegrationsEnabled(),
            "sso", plan.isSsoEnabled()));

    return ResponseEntity.ok(response);
  }

  @GetMapping("/plans")
  public ResponseEntity<List<Map<String, Object>>> getPlans() {
    List<Map<String, Object>> plans =
        Arrays.stream(BillingPlan.values())
            .map(
                p -> {
                  Map<String, Object> map = new LinkedHashMap<>();
                  map.put("id", p.name());
                  map.put("name", p.getName());
                  map.put("monthlyPrice", p.getMonthlyPriceUsd());
                  map.put("annualPrice", p.getAnnualPriceUsd());
                  map.put("maxQuestionnaires", p.getMaxQuestionnairesPerMonth());
                  map.put("maxDocuments", p.getMaxDocuments());
                  map.put("maxQuestions", p.getMaxQuestionsPerQuestionnaire());
                  map.put("apiAccess", p.isApiAccess());
                  map.put("integrations", p.isIntegrationsEnabled());
                  map.put("sso", p.isSsoEnabled());
                  return map;
                })
            .collect(Collectors.toList());
    return ResponseEntity.ok(plans);
  }

  @PostMapping("/subscribe")
  public ResponseEntity<Map<String, Object>> subscribe(@RequestBody SubscribeRequest request) {
    Tenant tenant = getCurrentTenant();

    try {
      Subscription subscription =
          billingService.createOrUpdateSubscription(
              tenant, request.plan(), request.paymentMethodId());
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("success", true);
      response.put("subscription", mapSubscription(subscription));
      response.put("clientSecret", "simulated_client_secret_" + subscription.getId());
      return ResponseEntity.ok(response);
    } catch (BillingException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @PostMapping("/cancel")
  public ResponseEntity<Map<String, Object>> cancel(@RequestBody CancelRequest request) {
    Tenant tenant = getCurrentTenant();

    try {
      Subscription subscription = billingService.cancelSubscription(tenant, request.atPeriodEnd());
      return ResponseEntity.ok(
          Map.of("success", true, "subscription", mapSubscription(subscription)));
    } catch (BillingException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @PostMapping("/resume")
  public ResponseEntity<Map<String, Object>> resume() {
    Tenant tenant = getCurrentTenant();

    try {
      Subscription subscription = billingService.resumeSubscription(tenant);
      return ResponseEntity.ok(
          Map.of("success", true, "subscription", mapSubscription(subscription)));
    } catch (BillingException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @GetMapping("/invoices")
  public ResponseEntity<Map<String, Object>> getInvoices(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
    Tenant tenant = getCurrentTenant();
    var invoices =
        billingService
            .getInvoiceRepository()
            .findByTenantIdAndDeletedAtIsNull(
                tenant.getId(), org.springframework.data.domain.PageRequest.of(page, size));

    Map<String, Object> response = new LinkedHashMap<>();
    response.put(
        "content",
        invoices.getContent().stream().map(this::mapInvoice).collect(Collectors.toList()));
    response.put("totalElements", invoices.getTotalElements());
    response.put("totalPages", invoices.getTotalPages());
    response.put("currentPage", page);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/usage")
  public ResponseEntity<Map<String, Object>> getUsage(
      @RequestParam String metric,
      @RequestParam String periodStart,
      @RequestParam String periodEnd) {
    Tenant tenant = getCurrentTenant();
    int usage =
        billingService.getUsage(
            tenant,
            metric,
            java.time.OffsetDateTime.parse(periodStart),
            java.time.OffsetDateTime.parse(periodEnd));

    return ResponseEntity.ok(Map.of("metric", metric, "usage", usage));
  }

  /**
   * Stripe posts here server-to-server with no bearer token, so the class-level role requirement
   * has to be lifted for this one method. The request is authenticated by its webhook signature
   * inside the service instead.
   */
  @PostMapping("/webhook")
  @PreAuthorize("permitAll()")
  public ResponseEntity<String> handleWebhook(
      @RequestHeader("Stripe-Signature") String sigHeader, @RequestBody String payload) {
    try {
      billingService.handleWebhookEvent(payload, sigHeader);
      return ResponseEntity.ok("OK");
    } catch (BillingService.BillingException e) {
      // 400 tells Stripe not to retry a request we will never accept.
      return ResponseEntity.badRequest().body("Webhook rejected: " + e.getMessage());
    } catch (Exception e) {
      // 500 asks Stripe to retry: the event was valid, we failed to apply it.
      return ResponseEntity.internalServerError().body("Webhook processing failed");
    }
  }

  private Map<String, Object> mapSubscription(Subscription s) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", s.getId());
    map.put("plan", s.getPlan().name());
    map.put("status", s.getStatus().name());
    map.put("stripeCustomerId", s.getStripeCustomerId());
    map.put("stripeSubscriptionId", s.getStripeSubscriptionId());
    map.put("currentPeriodStart", s.getCurrentPeriodStart());
    map.put("currentPeriodEnd", s.getCurrentPeriodEnd());
    map.put("cancelAtPeriodEnd", s.getCancelAtPeriodEnd());
    map.put("canceledAt", s.getCanceledAt());
    map.put("trialStart", s.getTrialStart());
    map.put("trialEnd", s.getTrialEnd());
    return map;
  }

  private Map<String, Object> mapInvoice(Invoice i) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", i.getId());
    map.put("invoiceNumber", i.getInvoiceNumber());
    map.put("status", i.getStatus().name());
    map.put("amountDue", i.getAmountDue());
    map.put("amountPaid", i.getAmountPaid());
    map.put("currency", i.getCurrency());
    map.put("dueDate", i.getDueDate());
    map.put("paidAt", i.getPaidAt());
    map.put("periodStart", i.getPeriodStart());
    map.put("periodEnd", i.getPeriodEnd());
    map.put("invoicePdfUrl", i.getInvoicePdfUrl());
    map.put("hostedInvoiceUrl", i.getHostedInvoiceUrl());
    return map;
  }

  private Tenant getCurrentTenant() {
    return TenantContext.requireCurrentTenant();
  }

  public record SubscribeRequest(BillingPlan plan, String paymentMethodId) {}

  public record CancelRequest(boolean atPeriodEnd) {}
}
