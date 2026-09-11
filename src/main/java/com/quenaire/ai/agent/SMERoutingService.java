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
package com.quenaire.ai.agent;

import com.quenaire.domain.sme.SMEAssignment;
import com.quenaire.domain.sme.SMEAssignmentRepository;
import com.quenaire.domain.sme.SMEAssignmentStatus;
import com.quenaire.domain.sme.SMEProfileEntity;
import com.quenaire.domain.sme.SMEProfileRepository;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.integration.slack.SlackIntegrationService;
import com.quenaire.security.TenantContext;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes low-confidence questions to the best-matched subject-matter expert and owns the durable
 * review queue behind the SME Collaboration Hub.
 */
@Service
public class SMERoutingService {

  private static final Logger log = LoggerFactory.getLogger(SMERoutingService.class);

  /** Default SLA for a routed question. */
  private static final int DEFAULT_SLA_HOURS = 24;

  private final SMEProfileRepository profileRepository;
  private final SMEAssignmentRepository assignmentRepository;
  private final SlackIntegrationService slackIntegrationService;

  public SMERoutingService(
      SMEProfileRepository profileRepository,
      SMEAssignmentRepository assignmentRepository,
      SlackIntegrationService slackIntegrationService) {
    this.profileRepository = profileRepository;
    this.assignmentRepository = assignmentRepository;
    this.slackIntegrationService = slackIntegrationService;
  }

  public record SMEProfile(
      UUID userId,
      String email,
      String fullName,
      List<String> expertiseTags,
      String slackId,
      int maxConcurrentReviews,
      boolean isActive,
      OffsetDateTime lastActiveAt,
      long activeReviewCount) {}

  public record RoutingResult(boolean success, UUID smeId, String message) {}

  @Transactional
  public RoutingResult routeToSME(
      UUID questionId, UUID questionnaireId, String category, UUID requestedById) {
    Tenant tenant = TenantContext.getCurrentTenant();
    if (tenant == null) {
      return new RoutingResult(false, null, "No tenant context available");
    }
    UUID tenantId = tenant.getId();

    log.info("Routing question {} to SME for category: {}", questionId, category);

    // An already-open assignment wins; re-routing would orphan the SME's work.
    Optional<SMEAssignment> existing =
        assignmentRepository.findOpenAssignment(tenantId, questionId);
    if (existing.isPresent()) {
      return new RoutingResult(true, existing.get().getSmeUserId(), "Already assigned");
    }

    List<SMEProfileEntity> candidates =
        profileRepository.findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(tenantId);
    if (candidates.isEmpty()) {
      log.warn("No active SMEs registered for tenant {}", tenantId);
      return new RoutingResult(false, null, "No SME available for category: " + category);
    }

    Optional<SMEProfileEntity> chosen = selectSME(tenantId, candidates, category);
    if (chosen.isEmpty()) {
      log.warn("All SMEs at capacity or no expertise match for category: {}", category);
      return new RoutingResult(
          false, null, "No SME with available capacity for category: " + category);
    }

    SMEProfileEntity sme = chosen.get();

    SMEAssignment assignment = new SMEAssignment();
    assignment.setQuestionId(questionId);
    assignment.setQuestionnaireId(questionnaireId);
    assignment.setSmeUserId(sme.getUserId());
    assignment.setAssignedById(requestedById);
    assignment.setCategory(category);
    assignment.setStatus(SMEAssignmentStatus.PENDING);
    assignment.setAssignedAt(OffsetDateTime.now());
    assignment.setDueAt(OffsetDateTime.now().plusHours(DEFAULT_SLA_HOURS));
    assignment.setTenant(tenant);
    assignmentRepository.save(assignment);

    notifySME(sme, questionId, questionnaireId);

    log.info("Routed question {} to SME {}", questionId, sme.getUserId());
    return new RoutingResult(true, sme.getUserId(), "Routed to SME");
  }

  /**
   * Pick the least-loaded SME whose expertise matches {@code category}, falling back to the
   * least-loaded SME overall. SMEs already at their concurrency cap are excluded, so a saturated
   * team returns empty rather than over-assigning.
   */
  private Optional<SMEProfileEntity> selectSME(
      UUID tenantId, List<SMEProfileEntity> candidates, String category) {
    Map<UUID, Long> workload = new HashMap<>();
    for (SMEProfileEntity sme : candidates) {
      workload.put(
          sme.getUserId(), assignmentRepository.countOpenForSme(tenantId, sme.getUserId()));
    }

    List<SMEProfileEntity> withCapacity =
        candidates.stream()
            .filter(sme -> workload.get(sme.getUserId()) < sme.getMaxConcurrentReviews())
            .toList();

    if (withCapacity.isEmpty()) {
      return Optional.empty();
    }

    Comparator<SMEProfileEntity> byLoad =
        Comparator.comparingLong(sme -> workload.get(sme.getUserId()));

    if (category != null && !category.isBlank()) {
      String lower = category.toLowerCase(Locale.ROOT);
      Optional<SMEProfileEntity> expert =
          withCapacity.stream()
              .filter(
                  sme ->
                      sme.getExpertiseTags().stream()
                          .anyMatch(
                              tag -> {
                                String t = tag.toLowerCase(Locale.ROOT);
                                return lower.contains(t) || t.contains(lower);
                              }))
              .min(byLoad);
      if (expert.isPresent()) {
        return expert;
      }
    }

    return withCapacity.stream().min(byLoad);
  }

  /** SME opens the item — starts the in-progress clock. */
  @Transactional
  public void claimReview(UUID questionId, UUID smeId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    assignmentRepository
        .findOpenAssignment(tenantId, questionId)
        .ifPresent(
            assignment -> {
              if (assignment.getStatus() == SMEAssignmentStatus.PENDING) {
                assignment.setStatus(SMEAssignmentStatus.IN_PROGRESS);
                assignment.setStartedAt(OffsetDateTime.now());
                assignmentRepository.save(assignment);
              }
            });
  }

  @Transactional
  public void completeReview(UUID questionId, UUID smeId, String action, String notes) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Optional<SMEAssignment> found = assignmentRepository.findOpenAssignment(tenantId, questionId);

    if (found.isEmpty()) {
      log.debug(
          "No open SME assignment for question {} — review completed outside the queue",
          questionId);
      return;
    }

    SMEAssignment assignment = found.get();
    assignment.setStatus(
        "APPROVE".equalsIgnoreCase(action)
            ? SMEAssignmentStatus.COMPLETED
            : SMEAssignmentStatus.ESCALATED);
    assignment.setCompletedAt(OffsetDateTime.now());
    assignment.setNotes(notes);
    assignmentRepository.save(assignment);

    log.info("SME {} completed review for question {} with action {}", smeId, questionId, action);
  }

  @Transactional(readOnly = true)
  public List<SMEAssignment> getSMEQueue(UUID smeId) {
    return assignmentRepository.findByTenantIdAndSmeUserIdAndDeletedAtIsNullOrderByDueAtAsc(
        TenantContext.getCurrentTenantId(), smeId);
  }

  @Transactional(readOnly = true)
  public List<SMEAssignment> getAllPendingReviews() {
    return assignmentRepository.findByTenantIdAndStatusInAndDeletedAtIsNullOrderByDueAtAsc(
        TenantContext.getCurrentTenantId(),
        List.of(SMEAssignmentStatus.PENDING, SMEAssignmentStatus.IN_PROGRESS));
  }

  @Transactional
  public SMEProfileEntity registerSME(
      UUID userId,
      String email,
      String fullName,
      List<String> expertiseTags,
      String slackId,
      int maxConcurrentReviews) {
    Tenant tenant = TenantContext.getCurrentTenant();
    if (tenant == null) {
      throw new IllegalStateException("No tenant context available");
    }

    SMEProfileEntity profile =
        profileRepository
            .findByTenantIdAndUserIdAndDeletedAtIsNull(tenant.getId(), userId)
            .orElseGet(
                () -> {
                  SMEProfileEntity created = new SMEProfileEntity();
                  created.setUserId(userId);
                  created.setTenant(tenant);
                  return created;
                });

    profile.setEmail(email);
    profile.setFullName(fullName);
    profile.setExpertiseTags(
        expertiseTags != null ? new ArrayList<>(expertiseTags) : new ArrayList<>());
    profile.setSlackId(slackId);
    if (maxConcurrentReviews > 0) {
      profile.setMaxConcurrentReviews(maxConcurrentReviews);
    }
    profile.setIsActive(true);
    profile.setLastActiveAt(OffsetDateTime.now());

    SMEProfileEntity saved = profileRepository.save(profile);
    log.info("Registered SME: {} ({})", saved.getFullName(), saved.getEmail());
    return saved;
  }

  @Transactional
  public void updateSMEAvailability(UUID smeId, boolean isActive, int maxConcurrent) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    profileRepository
        .findByTenantIdAndUserIdAndDeletedAtIsNull(tenantId, smeId)
        .ifPresent(
            profile -> {
              profile.setIsActive(isActive);
              if (maxConcurrent > 0) {
                profile.setMaxConcurrentReviews(maxConcurrent);
              }
              profile.setLastActiveAt(OffsetDateTime.now());
              profileRepository.save(profile);
            });
  }

  @Transactional(readOnly = true)
  public Optional<SMEProfile> getSMEProfile(UUID smeId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return profileRepository
        .findByTenantIdAndUserIdAndDeletedAtIsNull(tenantId, smeId)
        .map(entity -> toRecord(entity, assignmentRepository.countOpenForSme(tenantId, smeId)));
  }

  @Transactional(readOnly = true)
  public List<SMEProfile> listSMEs() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return profileRepository.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
        .map(
            entity ->
                toRecord(
                    entity, assignmentRepository.countOpenForSme(tenantId, entity.getUserId())))
        .toList();
  }

  /**
   * Profiles for a set of user ids, keyed by user id — avoids N+1 lookups when building the queue.
   */
  @Transactional(readOnly = true)
  public Map<UUID, SMEProfile> getProfilesByUserIds(Collection<UUID> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    UUID tenantId = TenantContext.getCurrentTenantId();
    Map<UUID, SMEProfile> result = new HashMap<>();
    for (SMEProfileEntity entity :
        profileRepository.findByTenantIdAndUserIdInAndDeletedAtIsNull(tenantId, userIds)) {
      result.put(
          entity.getUserId(),
          toRecord(entity, assignmentRepository.countOpenForSme(tenantId, entity.getUserId())));
    }
    return result;
  }

  private SMEProfile toRecord(SMEProfileEntity entity, long activeReviewCount) {
    return new SMEProfile(
        entity.getUserId(),
        entity.getEmail(),
        entity.getFullName(),
        List.copyOf(entity.getExpertiseTags()),
        entity.getSlackId(),
        entity.getMaxConcurrentReviews(),
        Boolean.TRUE.equals(entity.getIsActive()),
        entity.getLastActiveAt(),
        activeReviewCount);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getQueueMetrics() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return Map.of(
        "pending",
            assignmentRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
                tenantId, SMEAssignmentStatus.PENDING),
        "inProgress",
            assignmentRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
                tenantId, SMEAssignmentStatus.IN_PROGRESS),
        "completed",
            assignmentRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
                tenantId, SMEAssignmentStatus.COMPLETED),
        "escalated",
            assignmentRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
                tenantId, SMEAssignmentStatus.ESCALATED),
        "overdue", assignmentRepository.countOverdue(tenantId, OffsetDateTime.now()),
        "totalSMEs", profileRepository.countByTenantIdAndIsActiveTrueAndDeletedAtIsNull(tenantId),
        "activeSMEs", assignmentRepository.countActiveSmes(tenantId));
  }

  private void notifySME(SMEProfileEntity sme, UUID questionId, UUID questionnaireId) {
    if (sme.getSlackId() == null || sme.getSlackId().isBlank()) {
      log.debug("SME {} has no Slack id; skipping Slack notification", sme.getUserId());
      return;
    }

    try {
      // Slack chat.postMessage accepts a user id as the channel, which opens a DM.
      slackIntegrationService.sendMessage(
          sme.getSlackId(),
          String.format(
              "You have a new security questionnaire item to review.%nQuestionnaire: %s%nQuestion: %s",
              questionnaireId, questionId));
    } catch (Exception e) {
      // Notification failure must not roll back the routing decision.
      log.warn("Failed to notify SME {} via Slack", sme.getUserId(), e);
    }
  }
}
