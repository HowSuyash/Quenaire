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
package com.quenaire.api.rest.agent;

import com.quenaire.ai.agent.CollaborationService;
import com.quenaire.ai.agent.ReviewQueueService;
import com.quenaire.ai.agent.SMERoutingService;
import com.quenaire.ai.vanta.VantaReviewService;
import com.quenaire.domain.user.User;
import com.quenaire.security.TenantContext;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SME Collaboration Hub API: the review queue, inline edit/approve/reject actions, and the
 * real-time channel that keeps concurrent reviewers in sync.
 */
@RestController
@RequestMapping("/api/vanta/agent/sme")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
public class ReviewQueueController {

  private static final Logger log = LoggerFactory.getLogger(ReviewQueueController.class);

  private final ReviewQueueService reviewQueueService;
  private final VantaReviewService vantaReviewService;
  private final SMERoutingService smeRoutingService;
  private final CollaborationService collaborationService;

  public ReviewQueueController(
      ReviewQueueService reviewQueueService,
      VantaReviewService vantaReviewService,
      SMERoutingService smeRoutingService,
      CollaborationService collaborationService) {
    this.reviewQueueService = reviewQueueService;
    this.vantaReviewService = vantaReviewService;
    this.smeRoutingService = smeRoutingService;
    this.collaborationService = collaborationService;
  }

  // ---- Queue ------------------------------------------------------------

  @GetMapping("/queue")
  public ResponseEntity<ReviewQueueService.QueueResponse> getQueue(
      @RequestParam(required = false) UUID questionnaireId,
      @RequestParam(required = false) UUID smeUserId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String confidence,
      @RequestParam(required = false) String search,
      @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
      @RequestParam(required = false, defaultValue = "false") boolean mine) {

    UUID effectiveSme = mine ? TenantContext.getCurrentUserId() : smeUserId;

    ReviewQueueService.QueueFilter filter =
        new ReviewQueueService.QueueFilter(
            questionnaireId, effectiveSme, status, confidence, search, overdueOnly);

    return ResponseEntity.ok(reviewQueueService.getQueue(filter));
  }

  @GetMapping("/queue/stats")
  public ResponseEntity<Map<String, Object>> getQueueStats(
      @RequestParam(required = false) UUID questionnaireId) {

    ReviewQueueService.QueueResponse response =
        reviewQueueService.getQueue(
            new ReviewQueueService.QueueFilter(questionnaireId, null, null, null, null, false));

    Map<String, Object> body = new LinkedHashMap<>();
    ReviewQueueService.QueueStats stats = response.stats();
    body.put("total", stats.total());
    body.put("pending", stats.pending());
    body.put("inProgress", stats.inProgress());
    body.put("approved", stats.approved());
    body.put("rejected", stats.rejected());
    body.put("overdue", stats.overdue());
    body.put("highConfidence", stats.highConfidence());
    body.put("activeSMEs", stats.activeSMEs());
    body.putAll(smeRoutingService.getQueueMetrics());
    return ResponseEntity.ok(body);
  }

  @GetMapping("/queue/{questionId}")
  public ResponseEntity<ReviewQueueService.QueueItem> getQueueItem(@PathVariable UUID questionId) {
    return reviewQueueService
        .getItem(questionId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  // ---- Inline review actions --------------------------------------------

  public record ApproveRequest(String reviewNotes) {}

  public record RejectRequest(String reason) {}

  public record EditRequest(String answer, String reviewNotes) {}

  public record SelectOptionRequest(String selectedOption) {}

  public record BulkRequest(List<UUID> questionIds, String reviewNotes) {}

  @PostMapping("/queue/{questionId}/approve")
  public ResponseEntity<Map<String, Object>> approve(
      @PathVariable UUID questionId, @RequestBody(required = false) ApproveRequest request) {
    UUID userId = TenantContext.getCurrentUserId();
    String notes = request != null ? request.reviewNotes() : null;

    try {
      vantaReviewService.approveQuestion(questionId, userId, notes);
      smeRoutingService.completeReview(questionId, userId, "APPROVE", notes);
      return ResponseEntity.ok(broadcastAndBuild(questionId, userId, "approved"));
    } catch (IllegalStateException | IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @PostMapping("/queue/{questionId}/reject")
  public ResponseEntity<Map<String, Object>> reject(
      @PathVariable UUID questionId, @RequestBody(required = false) RejectRequest request) {
    UUID userId = TenantContext.getCurrentUserId();
    String reason = request != null ? request.reason() : null;

    try {
      vantaReviewService.rejectQuestion(questionId, userId, reason);
      smeRoutingService.completeReview(questionId, userId, "REJECT", reason);
      return ResponseEntity.ok(broadcastAndBuild(questionId, userId, "rejected"));
    } catch (IllegalStateException | IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  /** Inline edit — saves the human answer without approving it. */
  @PutMapping("/queue/{questionId}/answer")
  public ResponseEntity<Map<String, Object>> edit(
      @PathVariable UUID questionId, @RequestBody EditRequest request) {
    UUID userId = TenantContext.getCurrentUserId();

    if (request == null || request.answer() == null || request.answer().isBlank()) {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "Answer must not be empty"));
    }

    try {
      vantaReviewService.updateAnswer(questionId, userId, request.answer(), request.reviewNotes());
      return ResponseEntity.ok(broadcastAndBuild(questionId, userId, "edited"));
    } catch (IllegalStateException | IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @PostMapping("/queue/{questionId}/select-option")
  public ResponseEntity<Map<String, Object>> selectOption(
      @PathVariable UUID questionId, @RequestBody SelectOptionRequest request) {
    UUID userId = TenantContext.getCurrentUserId();

    try {
      vantaReviewService.selectOption(questionId, userId, request.selectedOption());
      return ResponseEntity.ok(broadcastAndBuild(questionId, userId, "option-selected"));
    } catch (IllegalStateException | IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  /** Edit and approve in a single round-trip — the common keyboard-driven path. */
  @PostMapping("/queue/{questionId}/edit-approve")
  public ResponseEntity<Map<String, Object>> editAndApprove(
      @PathVariable UUID questionId, @RequestBody EditRequest request) {
    UUID userId = TenantContext.getCurrentUserId();

    if (request == null || request.answer() == null || request.answer().isBlank()) {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "Answer must not be empty"));
    }

    try {
      vantaReviewService.updateAnswer(questionId, userId, request.answer(), request.reviewNotes());
      vantaReviewService.approveQuestion(questionId, userId, request.reviewNotes());
      smeRoutingService.completeReview(questionId, userId, "APPROVE", request.reviewNotes());
      return ResponseEntity.ok(broadcastAndBuild(questionId, userId, "edited-and-approved"));
    } catch (IllegalStateException | IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @PostMapping("/queue/bulk-approve")
  public ResponseEntity<Map<String, Object>> bulkApprove(@RequestBody BulkRequest request) {
    UUID userId = TenantContext.getCurrentUserId();

    if (request == null || request.questionIds() == null || request.questionIds().isEmpty()) {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "No questions selected"));
    }

    List<UUID> approved = new ArrayList<>();
    Map<String, String> errors = new LinkedHashMap<>();

    for (UUID questionId : request.questionIds()) {
      try {
        vantaReviewService.approveQuestion(questionId, userId, request.reviewNotes());
        smeRoutingService.completeReview(questionId, userId, "APPROVE", request.reviewNotes());
        approved.add(questionId);
        broadcastUpdate(questionId, userId);
      } catch (Exception e) {
        errors.put(questionId.toString(), e.getMessage());
        log.warn("Bulk approve failed for question {}", questionId, e);
      }
    }

    return ResponseEntity.ok(
        Map.of(
            "success", errors.isEmpty(),
            "approved", approved,
            "approvedCount", approved.size(),
            "failedCount", errors.size(),
            "errors", errors));
  }

  // ---- Real-time collaboration ------------------------------------------

  @GetMapping("/collab/stream")
  public SseEmitter stream() {
    UUID tenantId = collaborationService.requireTenantId();
    User user = TenantContext.getCurrentUser();

    UUID userId = user != null ? user.getId() : UUID.randomUUID();
    String name = displayName(user);
    String email = user != null ? user.getEmail() : "anonymous@local";

    return collaborationService.subscribe(tenantId, userId, name, email);
  }

  public record PresenceRequest(UUID focusedQuestionId, boolean editing) {}

  @PostMapping("/collab/presence")
  public ResponseEntity<Map<String, Object>> updatePresence(@RequestBody PresenceRequest request) {
    UUID tenantId = collaborationService.requireTenantId();
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      return ResponseEntity.ok(Map.of("success", false, "error", "No authenticated user"));
    }

    collaborationService.updatePresence(
        tenantId,
        userId,
        request != null ? request.focusedQuestionId() : null,
        request != null && request.editing());

    return ResponseEntity.ok(Map.of("success", true));
  }

  @GetMapping("/collab/presence")
  public ResponseEntity<Map<String, Object>> listPresence() {
    UUID tenantId = collaborationService.requireTenantId();
    return ResponseEntity.ok(
        Map.of(
            "users", collaborationService.listPresence(tenantId),
            "locks", collaborationService.currentLocks(tenantId)));
  }

  @PostMapping("/collab/lock/{questionId}")
  public ResponseEntity<Map<String, Object>> lock(@PathVariable UUID questionId) {
    UUID tenantId = collaborationService.requireTenantId();
    User user = TenantContext.getCurrentUser();
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("acquired", false, "error", "No authenticated user"));
    }

    Optional<String> heldBy =
        collaborationService.acquireLock(tenantId, questionId, userId, displayName(user));
    return heldBy
        .<ResponseEntity<Map<String, Object>>>map(
            holder ->
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("acquired", false, "heldBy", holder)))
        .orElseGet(() -> ResponseEntity.ok(Map.of("acquired", true)));
  }

  @DeleteMapping("/collab/lock/{questionId}")
  public ResponseEntity<Map<String, Object>> unlock(@PathVariable UUID questionId) {
    UUID tenantId = collaborationService.requireTenantId();
    UUID userId = TenantContext.getCurrentUserId();
    if (userId != null) {
      collaborationService.releaseLock(tenantId, questionId, userId);
    }
    return ResponseEntity.ok(Map.of("released", true));
  }

  // ---- SME roster -------------------------------------------------------

  public record RegisterSMERequest(
      UUID userId,
      String email,
      String fullName,
      List<String> expertiseTags,
      String slackId,
      Integer maxConcurrentReviews) {}

  @GetMapping("/roster")
  public ResponseEntity<List<SMERoutingService.SMEProfile>> listSMEs() {
    return ResponseEntity.ok(smeRoutingService.listSMEs());
  }

  @PostMapping("/roster")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<Map<String, Object>> registerSME(@RequestBody RegisterSMERequest request) {
    if (request == null || request.userId() == null || request.email() == null) {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "userId and email are required"));
    }

    smeRoutingService.registerSME(
        request.userId(),
        request.email(),
        request.fullName(),
        request.expertiseTags(),
        request.slackId(),
        request.maxConcurrentReviews() != null ? request.maxConcurrentReviews() : 10);

    return ResponseEntity.ok(Map.of("success", true));
  }

  public record AvailabilityRequest(boolean active, Integer maxConcurrentReviews) {}

  @PatchMapping("/roster/{smeUserId}/availability")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<Map<String, Object>> updateAvailability(
      @PathVariable UUID smeUserId, @RequestBody AvailabilityRequest request) {
    smeRoutingService.updateSMEAvailability(
        smeUserId,
        request.active(),
        request.maxConcurrentReviews() != null ? request.maxConcurrentReviews() : 0);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @PostMapping("/queue/{questionId}/claim")
  public ResponseEntity<Map<String, Object>> claim(@PathVariable UUID questionId) {
    UUID userId = TenantContext.getCurrentUserId();
    smeRoutingService.claimReview(questionId, userId);
    return ResponseEntity.ok(broadcastAndBuild(questionId, userId, "claimed"));
  }

  // ---- Helpers ----------------------------------------------------------

  /**
   * Reload the row, push it to every other reviewer, and return it so the acting client can
   * reconcile without a second request.
   */
  private Map<String, Object> broadcastAndBuild(UUID questionId, UUID actorId, String action) {
    Optional<ReviewQueueService.QueueItem> item = reviewQueueService.getItem(questionId);
    item.ifPresent(
        queueItem ->
            collaborationService.broadcastQuestionUpdate(
                collaborationService.requireTenantId(),
                queueItem,
                actorId,
                displayName(TenantContext.getCurrentUser())));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("success", true);
    body.put("action", action);
    body.put("questionId", questionId);
    item.ifPresent(queueItem -> body.put("item", queueItem));
    return body;
  }

  private void broadcastUpdate(UUID questionId, UUID actorId) {
    reviewQueueService
        .getItem(questionId)
        .ifPresent(
            item ->
                collaborationService.broadcastQuestionUpdate(
                    collaborationService.requireTenantId(),
                    item,
                    actorId,
                    displayName(TenantContext.getCurrentUser())));
  }

  private String displayName(User user) {
    if (user == null) {
      return "Unknown reviewer";
    }
    String first = user.getFirstName();
    String last = user.getLastName();
    if (first != null && last != null) {
      return first + " " + last;
    }
    if (first != null) {
      return first;
    }
    return user.getEmail() != null ? user.getEmail() : "Unknown reviewer";
  }
}
