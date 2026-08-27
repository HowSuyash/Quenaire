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
package com.complyai.ai.admin;

import com.complyai.domain.billing.Subscription;
import com.complyai.domain.billing.SubscriptionRepository;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.tenant.TenantPlan;
import com.complyai.domain.tenant.TenantRepository;
import com.complyai.domain.tenant.TenantStatus;
import com.complyai.domain.user.UserRepository;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-tenant operations for whoever runs the platform.
 *
 * <p>Every method here deliberately reaches across tenants, which is exactly what the rest of the
 * codebase forbids. It is therefore gated on the platform-level role rather than any tenant role,
 * and reports only aggregates and tenant metadata — never questionnaire or answer content, which
 * stays private to the customer even from the operator.
 */
@Service
public class PlatformAdminService {

  private static final Logger log = LoggerFactory.getLogger(PlatformAdminService.class);

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final VantaQuestionnaireRepository questionnaireRepository;

  public PlatformAdminService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      SubscriptionRepository subscriptionRepository,
      VantaQuestionnaireRepository questionnaireRepository) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.questionnaireRepository = questionnaireRepository;
  }

  public record TenantSummary(
      UUID id,
      String name,
      String slug,
      String customDomain,
      String status,
      String plan,
      Instant planExpiresAt,
      long userCount,
      long questionnaireCount,
      String subscriptionStatus,
      Instant createdAt) {}

  public record PlatformStats(
      long totalTenants,
      long activeTenants,
      long trialTenants,
      long suspendedTenants,
      long totalUsers,
      long totalQuestionnaires,
      Map<String, Long> tenantsByPlan,
      Map<String, Long> subscriptionsByStatus) {}

  @Transactional(readOnly = true)
  public List<TenantSummary> listTenants(String search, String status) {
    return tenantRepository.findAll().stream()
        .filter(t -> !t.isDeleted())
        .filter(t -> matchesSearch(t, search))
        .filter(
            t ->
                status == null || status.isBlank() || t.getStatus().name().equalsIgnoreCase(status))
        .map(this::toSummary)
        .sorted(
            Comparator.comparing(
                TenantSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  private boolean matchesSearch(Tenant tenant, String search) {
    if (search == null || search.isBlank()) return true;
    String needle = search.toLowerCase(Locale.ROOT);
    return contains(tenant.getName(), needle)
        || contains(tenant.getSlug(), needle)
        || contains(tenant.getCustomDomain(), needle);
  }

  private static boolean contains(String haystack, String needle) {
    return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
  }

  private TenantSummary toSummary(Tenant tenant) {
    Optional<Subscription> subscription =
        subscriptionRepository.findByTenantIdAndDeletedAtIsNull(tenant.getId());

    long questionnaires =
        questionnaireRepository.findAll().stream()
            .filter(q -> tenant.getId().equals(q.getTenantId()))
            .filter(q -> q.getDeletedAt() == null)
            .count();

    return new TenantSummary(
        tenant.getId(),
        tenant.getName(),
        tenant.getSlug(),
        tenant.getCustomDomain(),
        tenant.getStatus().name(),
        tenant.getPlan().name(),
        tenant.getPlanExpiresAt(),
        userRepository.countByTenantIdAndDeletedAtIsNull(tenant.getId()),
        questionnaires,
        subscription.map(s -> s.getStatus().name()).orElse("NONE"),
        tenant.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public PlatformStats getStats() {
    List<Tenant> tenants = tenantRepository.findAll().stream().filter(t -> !t.isDeleted()).toList();

    Map<String, Long> byPlan =
        tenants.stream()
            .collect(
                Collectors.groupingBy(
                    t -> t.getPlan().name(), TreeMap::new, Collectors.counting()));

    Map<String, Long> bySubscription =
        subscriptionRepository.findAll().stream()
            .filter(s -> !s.isDeleted())
            .collect(
                Collectors.groupingBy(
                    s -> s.getStatus().name(), TreeMap::new, Collectors.counting()));

    long users =
        tenants.stream()
            .mapToLong(t -> userRepository.countByTenantIdAndDeletedAtIsNull(t.getId()))
            .sum();

    long questionnaires =
        questionnaireRepository.findAll().stream().filter(q -> q.getDeletedAt() == null).count();

    return new PlatformStats(
        tenants.size(),
        tenants.stream().filter(t -> t.getStatus() == TenantStatus.ACTIVE).count(),
        tenants.stream().filter(t -> t.getStatus() == TenantStatus.TRIAL).count(),
        tenants.stream().filter(t -> t.getStatus() == TenantStatus.SUSPENDED).count(),
        users,
        questionnaires,
        byPlan,
        bySubscription);
  }

  @Transactional
  public TenantSummary setTenantStatus(UUID tenantId, TenantStatus status) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

    log.warn(
        "Platform admin changed tenant {} status: {} -> {}", tenantId, tenant.getStatus(), status);

    tenant.setStatus(status);
    return toSummary(tenantRepository.save(tenant));
  }

  /**
   * Override a tenant's plan without going through Stripe.
   *
   * <p>For contracted/enterprise customers billed outside the self-serve flow. A later Stripe
   * webhook will overwrite this, so it is not a way to hand out free upgrades on a Stripe-managed
   * account.
   */
  @Transactional
  public TenantSummary setTenantPlan(UUID tenantId, TenantPlan plan, Instant expiresAt) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

    log.warn("Platform admin overrode tenant {} plan: {} -> {}", tenantId, tenant.getPlan(), plan);

    tenant.setPlan(plan);
    tenant.setPlanExpiresAt(expiresAt);
    return toSummary(tenantRepository.save(tenant));
  }

  @Transactional
  public void softDeleteTenant(UUID tenantId) {
    tenantRepository
        .findById(tenantId)
        .ifPresent(
            tenant -> {
              log.warn("Platform admin soft-deleted tenant {}", tenantId);
              tenant.setStatus(TenantStatus.DELETED);
              tenant.softDelete();
              tenantRepository.save(tenant);
            });
  }
}
