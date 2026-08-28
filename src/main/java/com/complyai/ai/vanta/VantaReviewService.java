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
package com.complyai.ai.vanta;

import com.complyai.domain.questionnaire.vanta.*;
import com.complyai.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VantaReviewService {

  private static final Logger log = LoggerFactory.getLogger(VantaReviewService.class);

  private final VantaQuestionnaireRepository questionnaireRepository;
  private final VantaQuestionRepository questionRepository;
  private final AnswerLibraryService answerLibraryService;
  private final VantaReasoningEngine reasoningEngine;

  public VantaReviewService(
      VantaQuestionnaireRepository questionnaireRepository,
      VantaQuestionRepository questionRepository,
      AnswerLibraryService answerLibraryService,
      VantaReasoningEngine reasoningEngine) {
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
    this.answerLibraryService = answerLibraryService;
    this.reasoningEngine = reasoningEngine;
  }

  @Transactional
  public BulkApprovalResult approveAllHighConfidence(
      UUID questionnaireId, UUID approvedById, int threshold) {
    log.info(
        "Bulk approving high confidence questions for questionnaire: {} (threshold: {})",
        questionnaireId,
        threshold);

    List<VantaQuestion> highConfidenceQuestions =
        questionRepository
            .findByQuestionnaireIdAndConfidenceScoreGreaterThanEqual(questionnaireId, threshold)
            .stream()
            .filter(
                q ->
                    q.getStatus() == VantaQuestion.QuestionStatus.AI_DRAFTED
                        || q.getStatus() == VantaQuestion.QuestionStatus.PENDING
                        || q.getStatus() == VantaQuestion.QuestionStatus.NEEDS_REVIEW)
            .collect(Collectors.toList());

    int approved = 0;
    int failed = 0;
    List<UUID> approvedIds = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (VantaQuestion question : highConfidenceQuestions) {
      try {
        approveQuestion(
            question.getId(), approvedById, "Auto-approved: confidence > " + threshold + "%");
        approvedIds.add(question.getId());
        approved++;
      } catch (Exception e) {
        failed++;
        errors.add("Question " + question.getQuestionNumber() + ": " + e.getMessage());
        log.error("Failed to approve question {}", question.getId(), e);
      }
    }

    // Update questionnaire stats
    updateQuestionnaireStats(questionnaireId);

    log.info("Bulk approval complete: {} approved, {} failed", approved, failed);
    return new BulkApprovalResult(approved, failed, approvedIds, errors);
  }

  @Transactional
  public VantaQuestion approveQuestion(UUID questionId, UUID approvedById, String reviewNotes) {
    VantaQuestion question =
        questionRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(questionId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));

    if (question.getStatus() == VantaQuestion.QuestionStatus.APPROVED) {
      throw new IllegalStateException("Question already approved");
    }

    // Create version history entry
    createVersionHistory(question, "APPROVED", approvedById);

    question.setStatus(VantaQuestion.QuestionStatus.APPROVED);
    question.setFinalAnswer(question.getAiAnswer());
    question.setReviewNotes(reviewNotes);
    question.setReviewedById(approvedById);
    question.setReviewedAt(OffsetDateTime.now());
    question.setApprovedById(approvedById);
    question.setApprovedAt(OffsetDateTime.now());

    // Add to Answer Library for future reuse
    answerLibraryService.createEntry(question, question.getAiAnswer(), approvedById);

    VantaQuestion saved = questionRepository.save(question);

    // Update questionnaire stats
    updateQuestionnaireStats(question.getQuestionnaireId());

    return saved;
  }

  @Transactional
  public VantaQuestion rejectQuestion(UUID questionId, UUID rejectedById, String reason) {
    VantaQuestion question =
        questionRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(questionId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));

    createVersionHistory(question, "REJECTED", rejectedById);

    question.setStatus(VantaQuestion.QuestionStatus.REJECTED);
    question.setReviewNotes(reason);
    question.setReviewedById(rejectedById);
    question.setReviewedAt(OffsetDateTime.now());

    VantaQuestion saved = questionRepository.save(question);
    updateQuestionnaireStats(question.getQuestionnaireId());

    return saved;
  }

  @Transactional
  public VantaQuestion delegateQuestion(
      UUID questionId, UUID delegatedById, UUID assignedToId, String notes) {
    VantaQuestion question =
        questionRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(questionId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));

    createVersionHistory(question, "DELEGATED", delegatedById);

    question.setStatus(VantaQuestion.QuestionStatus.DELEGATED);
    question.setAssignedToId(assignedToId);
    question.setReviewNotes(notes);
    question.setReviewedById(delegatedById);
    question.setReviewedAt(OffsetDateTime.now());

    VantaQuestion saved = questionRepository.save(question);
    updateQuestionnaireStats(question.getQuestionnaireId());

    // TODO: Send notification to assignee

    return saved;
  }

  @Transactional
  public VantaQuestion updateAnswer(
      UUID questionId, UUID updatedById, String humanAnswer, String reviewNotes) {
    VantaQuestion question =
        questionRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(questionId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));

    createVersionHistory(question, "HUMAN_EDITED", updatedById);

    question.setHumanAnswer(humanAnswer);
    question.setFinalAnswer(humanAnswer);
    question.setReviewNotes(reviewNotes);
    question.setStatus(VantaQuestion.QuestionStatus.HUMAN_EDITED);
    question.setReviewedById(updatedById);
    question.setReviewedAt(OffsetDateTime.now());

    VantaQuestion saved = questionRepository.save(question);
    updateQuestionnaireStats(question.getQuestionnaireId());

    return saved;
  }

  @Transactional
  public VantaQuestion selectOption(UUID questionId, UUID updatedById, String selectedOption) {
    VantaQuestion question =
        questionRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(questionId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));

    if (!question.getQuestionType().hasOptions()) {
      throw new IllegalStateException("Question does not have options");
    }

    if (question.getOptions() != null && !question.getOptions().contains(selectedOption)) {
      throw new IllegalArgumentException("Invalid option: " + selectedOption);
    }

    createVersionHistory(question, "OPTION_SELECTED", updatedById);

    question.setSelectedOption(selectedOption);
    question.setFinalAnswer(selectedOption);
    question.setStatus(VantaQuestion.QuestionStatus.AI_DRAFTED);
    question.setReviewedById(updatedById);
    question.setReviewedAt(OffsetDateTime.now());

    VantaQuestion saved = questionRepository.save(question);
    updateQuestionnaireStats(question.getQuestionnaireId());

    return saved;
  }

  public List<QuestionVersionHistory> getVersionHistory(UUID questionId) {
    // In production, this would query a version history table
    // For now, return from metadata
    // Scoped: the id comes from a URL, and findById is not restricted to anything.
    VantaQuestion question =
        questionRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(
                questionId, TenantContext.requireCurrentTenant().getId())
            .orElse(null);
    if (question == null) return new ArrayList<>();

    String historyJson = question.getMetadataJson();
    // Parse and return history
    return new ArrayList<>();
  }

  private void createVersionHistory(VantaQuestion question, String action, UUID userId) {
    Map<String, Object> historyEntry = new HashMap<>();
    historyEntry.put("action", action);
    historyEntry.put("userId", userId);
    historyEntry.put("timestamp", OffsetDateTime.now().toString());
    historyEntry.put("previousStatus", question.getStatus());
    historyEntry.put("previousAnswer", question.getFinalAnswer());

    // Store in metadata JSON (in production, use separate history table)
    List<Map<String, Object>> history = new ArrayList<>();
    try {
      String existing = question.getMetadataJson();
      if (existing != null && !existing.isEmpty()) {
        history = new ObjectMapper().readValue(existing, List.class);
      }
    } catch (Exception e) {
      log.warn("Failed to parse existing history", e);
    }

    history.add(0, historyEntry);

    try {
      question.setMetadataJson(new ObjectMapper().writeValueAsString(history));
    } catch (Exception e) {
      log.warn("Failed to serialize history", e);
    }
  }

  @Transactional
  public void updateQuestionnaireStats(UUID questionnaireId) {
    VantaQuestionnaire questionnaire =
        questionnaireRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(
                questionnaireId, TenantContext.requireCurrentTenant().getId())
            .orElse(null);
    if (questionnaire == null) return;

    List<VantaQuestion> questions =
        questionRepository.findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(
            questionnaireId);

    questionnaire.setTotalQuestions(questions.size());
    questionnaire.setAnsweredQuestions(
        (int)
            questions.stream()
                .filter(q -> q.getFinalAnswer() != null && !q.getFinalAnswer().isEmpty())
                .count());
    questionnaire.setApprovedQuestions(
        (int)
            questions.stream()
                .filter(q -> q.getStatus() == VantaQuestion.QuestionStatus.APPROVED)
                .count());
    questionnaire.setHighConfidenceQuestions(
        (int)
            questions.stream()
                .filter(q -> q.getConfidenceScore() != null && q.getConfidenceScore() >= 90)
                .count());
    questionnaire.setNeedsReviewQuestions(
        (int)
            questions.stream()
                .filter(q -> q.getStatus() == VantaQuestion.QuestionStatus.NEEDS_REVIEW)
                .count());
    questionnaire.setDelegatedQuestions(
        (int)
            questions.stream()
                .filter(q -> q.getStatus() == VantaQuestion.QuestionStatus.DELEGATED)
                .count());

    if (questionnaire.getApprovedQuestions() == questionnaire.getTotalQuestions()
        && questionnaire.getTotalQuestions() > 0) {
      questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.APPROVED);
      questionnaire.setCompletedAt(OffsetDateTime.now());
    } else if (questionnaire.getAnsweredQuestions() > 0) {
      questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.IN_REVIEW);
    }

    questionnaireRepository.save(questionnaire);
  }

  public ReviewStats getReviewStats(UUID questionnaireId) {
    List<VantaQuestion> questions =
        questionRepository.findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(
            questionnaireId);

    Map<VantaQuestion.QuestionStatus, Long> statusCounts =
        questions.stream()
            .collect(Collectors.groupingBy(VantaQuestion::getStatus, Collectors.counting()));

    return ReviewStats.builder()
        .totalQuestions(questions.size())
        .approved(statusCounts.getOrDefault(VantaQuestion.QuestionStatus.APPROVED, 0L))
        .aiDrafted(statusCounts.getOrDefault(VantaQuestion.QuestionStatus.AI_DRAFTED, 0L))
        .needsReview(statusCounts.getOrDefault(VantaQuestion.QuestionStatus.NEEDS_REVIEW, 0L))
        .rejected(statusCounts.getOrDefault(VantaQuestion.QuestionStatus.REJECTED, 0L))
        .delegated(statusCounts.getOrDefault(VantaQuestion.QuestionStatus.DELEGATED, 0L))
        .pending(statusCounts.getOrDefault(VantaQuestion.QuestionStatus.PENDING, 0L))
        .highConfidence(
            questions.stream()
                .filter(q -> q.getConfidenceScore() != null && q.getConfidenceScore() >= 90)
                .count())
        .build();
  }

  // Result classes
  public static class BulkApprovalResult {
    private final int approved;
    private final int failed;
    private final List<UUID> approvedIds;
    private final List<String> errors;

    public BulkApprovalResult(
        int approved, int failed, List<UUID> approvedIds, List<String> errors) {
      this.approved = approved;
      this.failed = failed;
      this.approvedIds = approvedIds;
      this.errors = errors;
    }

    public int getApproved() {
      return approved;
    }

    public int getFailed() {
      return failed;
    }

    public List<UUID> getApprovedIds() {
      return approvedIds;
    }

    public List<String> getErrors() {
      return errors;
    }
  }

  public static class ReviewStats {
    private int totalQuestions;
    private long approved;
    private long aiDrafted;
    private long needsReview;
    private long rejected;
    private long delegated;
    private long pending;
    private long highConfidence;

    public int getTotalQuestions() {
      return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
      this.totalQuestions = totalQuestions;
    }

    public long getApproved() {
      return approved;
    }

    public void setApproved(long approved) {
      this.approved = approved;
    }

    public long getAiDrafted() {
      return aiDrafted;
    }

    public void setAiDrafted(long aiDrafted) {
      this.aiDrafted = aiDrafted;
    }

    public long getNeedsReview() {
      return needsReview;
    }

    public void setNeedsReview(long needsReview) {
      this.needsReview = needsReview;
    }

    public long getRejected() {
      return rejected;
    }

    public void setRejected(long rejected) {
      this.rejected = rejected;
    }

    public long getDelegated() {
      return delegated;
    }

    public void setDelegated(long delegated) {
      this.delegated = delegated;
    }

    public long getPending() {
      return pending;
    }

    public void setPending(long pending) {
      this.pending = pending;
    }

    public long getHighConfidence() {
      return highConfidence;
    }

    public void setHighConfidence(long highConfidence) {
      this.highConfidence = highConfidence;
    }

    public static ReviewStatsBuilder builder() {
      return new ReviewStatsBuilder();
    }

    public static class ReviewStatsBuilder {
      private int totalQuestions;
      private long approved;
      private long aiDrafted;
      private long needsReview;
      private long rejected;
      private long delegated;
      private long pending;
      private long highConfidence;

      public ReviewStatsBuilder totalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
        return this;
      }

      public ReviewStatsBuilder approved(long approved) {
        this.approved = approved;
        return this;
      }

      public ReviewStatsBuilder aiDrafted(long aiDrafted) {
        this.aiDrafted = aiDrafted;
        return this;
      }

      public ReviewStatsBuilder needsReview(long needsReview) {
        this.needsReview = needsReview;
        return this;
      }

      public ReviewStatsBuilder rejected(long rejected) {
        this.rejected = rejected;
        return this;
      }

      public ReviewStatsBuilder delegated(long delegated) {
        this.delegated = delegated;
        return this;
      }

      public ReviewStatsBuilder pending(long pending) {
        this.pending = pending;
        return this;
      }

      public ReviewStatsBuilder highConfidence(long highConfidence) {
        this.highConfidence = highConfidence;
        return this;
      }

      public ReviewStats build() {
        ReviewStats rs = new ReviewStats();
        rs.totalQuestions = this.totalQuestions;
        rs.approved = this.approved;
        rs.aiDrafted = this.aiDrafted;
        rs.needsReview = this.needsReview;
        rs.rejected = this.rejected;
        rs.delegated = this.delegated;
        rs.pending = this.pending;
        rs.highConfidence = this.highConfidence;
        return rs;
      }
    }
  }

  public static class QuestionVersionHistory {
    private String action;
    private UUID userId;
    private OffsetDateTime timestamp;
    private VantaQuestion.QuestionStatus previousStatus;
    private String previousAnswer;

    // Getters/Setters
    public String getAction() {
      return action;
    }

    public void setAction(String action) {
      this.action = action;
    }

    public UUID getUserId() {
      return userId;
    }

    public void setUserId(UUID userId) {
      this.userId = userId;
    }

    public OffsetDateTime getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
      this.timestamp = timestamp;
    }

    public VantaQuestion.QuestionStatus getPreviousStatus() {
      return previousStatus;
    }

    public void setPreviousStatus(VantaQuestion.QuestionStatus previousStatus) {
      this.previousStatus = previousStatus;
    }

    public String getPreviousAnswer() {
      return previousAnswer;
    }

    public void setPreviousAnswer(String previousAnswer) {
      this.previousAnswer = previousAnswer;
    }
  }
}
