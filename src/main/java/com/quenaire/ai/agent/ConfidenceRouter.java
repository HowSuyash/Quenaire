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

import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConfidenceRouter {

  // Confidence thresholds
  private static final int AUTO_APPROVE_THRESHOLD = 85;
  private static final int SME_QUEUE_THRESHOLD = 60;

  // Below 60 -> manual review

  public enum RouteDecision {
    AUTO_APPROVE,
    SME_QUEUE,
    MANUAL_REVIEW,
    RE_ATTEMPT
  }

  public record RoutingDecision(
      RouteDecision decision,
      String reason,
      UUID assignedToId, // For SME_QUEUE
      String assignedSMEEmail, // For notification
      int confidenceScore) {}

  public RoutingDecision route(VantaQuestion question) {
    Integer confidence = question.getConfidenceScore();

    if (confidence == null) {
      return new RoutingDecision(
          RouteDecision.MANUAL_REVIEW, "No confidence score available", null, null, 0);
    }

    if (confidence >= 85) {
      return new RoutingDecision(
          RouteDecision.AUTO_APPROVE,
          String.format("High confidence (%d%%) - auto-approved", confidence),
          null,
          null,
          confidence);
    } else if (confidence >= 60) {
      // Route to SME based on question category
      UUID smeId = findSMEForCategory(question.getCategory()).orElse(null);
      String smeEmail = smeId == null ? null : getSMEEmail(smeId).orElse(null);

      // The reason says what actually happened. It read "routed to SME" while
      // assigning nobody, so the queue showed questions as handed to an expert
      // when no one had been given them and no one had been told.
      return new RoutingDecision(
          RouteDecision.SME_QUEUE,
          smeId == null
              ? String.format(
                  "Medium confidence (%d%%) - queued for review, no expert assigned", confidence)
              : String.format("Medium confidence (%d%%) - routed to SME", confidence),
          smeId,
          smeEmail,
          confidence);
    } else {
      return new RoutingDecision(
          RouteDecision.MANUAL_REVIEW,
          String.format("Low confidence (%d%%) - requires manual review", confidence),
          null,
          null,
          confidence);
    }
  }

  public RoutingDecision routeWithReAttempt(VantaQuestion question, int attemptNumber) {
    RoutingDecision initial = route(question);

    if (initial.decision() == RouteDecision.MANUAL_REVIEW && attemptNumber < 2) {
      // Try LLM re-attempt with chain-of-thought
      return new RoutingDecision(
          RouteDecision.RE_ATTEMPT,
          String.format(
              "Low confidence (%d%%) - attempting LLM re-attempt #%d",
              question.getConfidenceScore(), attemptNumber + 1),
          null,
          null,
          question.getConfidenceScore() != null ? question.getConfidenceScore() : 0);
    }

    return initial;
  }

  /**
   * The subject-matter expert who should answer a question in this category.
   *
   * <p>Always empty at present, and deliberately so: assigning a question to a person requires
   * knowing who on the team knows what, and nothing in the data model records that -- users have a
   * role (OWNER, ADMIN, MEMBER, VIEWER) and nothing about expertise.
   *
   * <p>This used to be a ladder of category checks whose branches were all commented out, so it
   * read as working routing logic and returned null from every path. Naming the missing piece is
   * more useful than looking like it does something.
   *
   * <p>Implementing it needs a product decision first -- whether expertise is a tag on a user, a
   * named queue people subscribe to, or simply a rota -- and then a table to hold it.
   */
  private Optional<UUID> findSMEForCategory(String category) {
    return Optional.empty();
  }

  /** The expert's address, for notifying them. Empty for the same reason as above. */
  private Optional<String> getSMEEmail(UUID smeId) {
    return Optional.empty();
  }

  /**
   * Not implemented.
   *
   * <p>The thresholds are compile-time constants. This accepted new values and discarded them, so a
   * caller changing them saw no error and no change -- worse than not offering the method at all.
   * Making them configurable means storing them per tenant.
   */
  public void updateThresholds(int autoApprove, int smeQueue) {
    throw new UnsupportedOperationException(
        "Confidence thresholds are not configurable yet; they are constants in ConfidenceRouter");
  }

  public int getAutoApproveThreshold() {
    return AUTO_APPROVE_THRESHOLD;
  }

  public int getSmeQueueThreshold() {
    return SME_QUEUE_THRESHOLD;
  }
}
