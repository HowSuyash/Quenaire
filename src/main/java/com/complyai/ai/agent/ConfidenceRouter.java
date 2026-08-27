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
package com.complyai.ai.agent;

import com.complyai.domain.questionnaire.vanta.VantaQuestion;
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
      UUID smeId = findSMEForCategory(question.getCategory());
      String smeEmail = smeId != null ? getSMEEmail(smeId) : null;

      return new RoutingDecision(
          RouteDecision.SME_QUEUE,
          String.format("Medium confidence (%d%%) - routed to SME", confidence),
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

  private UUID findSMEForCategory(String category) {
    // In production, query user database for SME with matching expertise tags
    // For now, return null to indicate no specific SME found
    if (category == null) return null;

    String lower = category.toLowerCase();
    if (lower.contains("crypto") || lower.contains("encryption") || lower.contains("key")) {
      // return smeRepository.findByExpertiseTag("cryptography");
    } else if (lower.contains("network") || lower.contains("firewall") || lower.contains("vpn")) {
      // return smeRepository.findByExpertiseTag("network-security");
    } else if (lower.contains("privacy") || lower.contains("gdpr") || lower.contains("dpa")) {
      // return smeRepository.findByExpertiseTag("privacy-law");
    } else if (lower.contains("incident")
        || lower.contains("response")
        || lower.contains("breach")) {
      // return smeRepository.findByExpertiseTag("incident-response");
    } else if (lower.contains("access")
        || lower.contains("mfa")
        || lower.contains("authentication")) {
      // return smeRepository.findByExpertiseTag("identity-access");
    }
    return null;
  }

  private String getSMEEmail(UUID smeId) {
    // In production, query user service
    return null;
  }

  public void updateThresholds(int autoApprove, int smeQueue) {
    // In production, persist to database
  }

  public int getAutoApproveThreshold() {
    return AUTO_APPROVE_THRESHOLD;
  }

  public int getSmeQueueThreshold() {
    return SME_QUEUE_THRESHOLD;
  }
}
