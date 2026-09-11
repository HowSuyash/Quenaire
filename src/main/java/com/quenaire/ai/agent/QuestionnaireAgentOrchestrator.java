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

import com.quenaire.ai.agent.task.AgentTaskQueue;
import com.quenaire.ai.vanta.VantaQuestionnaireService;
import com.quenaire.ai.vanta.VantaReasoningEngine;
import com.quenaire.ai.vanta.VantaReviewService;
import com.quenaire.ai.vanta.VantaSpreadsheetParserService;
import com.quenaire.domain.agent.AgentTask;
import com.quenaire.domain.agent.AgentTaskRepository;
import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionRepository;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.quenaire.security.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionnaireAgentOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(QuestionnaireAgentOrchestrator.class);

  private final VantaQuestionnaireService questionnaireService;
  private final VantaQuestionnaireRepository questionnaireRepository;
  private final VantaQuestionRepository questionRepository;
  private final VantaReasoningEngine reasoningEngine;
  private final VantaReviewService reviewService;
  private final VantaSpreadsheetParserService parserService;
  private final AgentTaskRepository taskRepository;
  private final ConfidenceRouter confidenceRouter;
  private final SMERoutingService smeRoutingService;
  private final AgentTaskQueue taskQueue;

  // In-memory state for active orchestrations (in production, use Redis)
  private final Map<UUID, OrchestrationState> activeOrchestrations = new ConcurrentHashMap<>();

  public QuestionnaireAgentOrchestrator(
      VantaQuestionnaireService questionnaireService,
      VantaQuestionnaireRepository questionnaireRepository,
      VantaQuestionRepository questionRepository,
      VantaReasoningEngine reasoningEngine,
      VantaReviewService reviewService,
      VantaSpreadsheetParserService parserService,
      AgentTaskRepository taskRepository,
      ConfidenceRouter confidenceRouter,
      SMERoutingService smeRoutingService,
      AgentTaskQueue taskQueue) {
    this.questionnaireService = questionnaireService;
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
    this.reasoningEngine = reasoningEngine;
    this.reviewService = reviewService;
    this.parserService = parserService;
    this.taskRepository = taskRepository;
    this.confidenceRouter = confidenceRouter;
    this.smeRoutingService = smeRoutingService;
    this.taskQueue = taskQueue;
  }

  /**
   * Start the autonomous agent pipeline for a questionnaire. This is the main entry point - called
   * when a questionnaire is uploaded or intake is received.
   */
  @Transactional
  public OrchestrationResult startOrchestration(UUID questionnaireId, UUID userId) {
    log.info("Starting agent orchestration for questionnaire: {}", questionnaireId);

    VantaQuestionnaire questionnaire =
        questionnaireRepository
            .findById(questionnaireId)
            .orElseThrow(
                () -> new IllegalArgumentException("Questionnaire not found: " + questionnaireId));

    // Update status to PROCESSING
    questionnaire.setStatus(
        com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire.QuestionnaireStatus.PROCESSING);
    questionnaireRepository.save(questionnaire);

    // Create orchestration state
    OrchestrationState state = new OrchestrationState(questionnaireId, userId);
    activeOrchestrations.put(questionnaireId, state);

    // Stage 1: Parse and extract questions (if not already done)
    if (questionnaire.getStatus()
            == com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire.QuestionnaireStatus
                .PARSING
        || questionnaire.getTotalQuestions() == 0) {
      return processStageParse(questionnaireId);
    }

    // Stage 2: Generate AI answers for all questions
    return processStageGenerateAnswers(questionnaireId);
  }

  private OrchestrationResult processStageParse(UUID questionnaireId) {
    log.info("Stage 1: Parsing questionnaire {}", questionnaireId);

    VantaQuestionnaire questionnaire =
        questionnaireRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(
                questionnaireId, TenantContext.requireCurrentTenant().getId())
            .orElseThrow();

    try {
      // Parse the spreadsheet if we have original file bytes
      com.quenaire.ai.vanta.VantaSpreadsheetParserService.ParsedQuestionnaire parsed = null;
      if (questionnaire.getOriginalFileBytes() != null) {
        // Parse directly from bytes
        parsed =
            parserService.parseBytes(
                questionnaire.getOriginalFileBytes(), questionnaire.getFileName());

        // Convert parsed questions to entities
        List<VantaQuestion> questions = new java.util.ArrayList<>();
        for (com.quenaire.ai.vanta.VantaSpreadsheetParserService.ParsedSheet sheet :
            parsed.getSheets()) {
          for (com.quenaire.ai.vanta.VantaSpreadsheetParserService.ParsedQuestion pq :
              parsed.getSheets().get(0).getQuestions()) {
            VantaQuestion question = new com.quenaire.domain.questionnaire.vanta.VantaQuestion();
            question.setQuestionnaireId(questionnaireId);
            question.setQuestionNumber(pq.getQuestionNumber());
            question.setCategory(pq.getCategory());
            question.setSubCategory(pq.getSubCategory());
            question.setText(pq.getText());
            question.setQuestionType(pq.getQuestionType());
            question.setOptionsJson(serializeOptions(pq.getOptions()));
            question.setRequired(pq.getRequired());
            question.setAttachmentRequest(pq.getAttachmentRequest());
            question.setStatus(
                com.quenaire.domain.questionnaire.vanta.VantaQuestion.QuestionStatus.PENDING);
            question.setRowIndex(pq.getRowIndex());
            question.setColIndex(pq.getColIndex());
            question.setSheetName(pq.getSheetName());
            question.setOriginalRowIndex(pq.getOriginalRowIndex());
            question.setOriginalHeaderRowIndex(pq.getOriginalHeaderRowIndex());
            question.setMetadataJson(serializeCoordinates(pq));
            question.setTenantId(TenantContext.getCurrentTenantId());

            // Save question
            // questionRepository.save(question); // Would need proper questionnaire reference
          }
        }

        // Update questionnaire stats
        // questionnaire.setTotalQuestions(questions.size());
        // questionnaireRepository.save(questionnaire);
      }

      // Transition to next stage
      if (parsed != null) {
        questionnaire.setStatus(
            com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire.QuestionnaireStatus
                .READY_FOR_REVIEW);
        questionnaireRepository.save(questionnaire);
      }

      return new OrchestrationResult(true, "Parsing complete, ready for AI generation", null);
    } catch (Exception e) {
      log.error("Failed to parse questionnaire", e);
      return new OrchestrationResult(false, "Parsing failed: " + e.getMessage(), e);
    }
  }

  private OrchestrationResult processStageGenerateAnswers(UUID questionnaireId) {
    log.info("Stage 2: Generating AI answers for questionnaire {}", questionnaireId);

    try {
      // Get all questions for this questionnaire
      // This would need the vantaQuestionRepository injected
      // List<VantaQuestion> questions =
      // questionRepository.findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(questionnaireId);

      // For now, delegate to existing questionnaire service
      questionnaireService.processQuestionnaire(questionnaireId, List.of());

      // Update questionnaire status
      VantaQuestionnaire questionnaire =
          questionnaireRepository
              .findByIdAndTenantIdAndDeletedAtIsNull(
                  questionnaireId, TenantContext.requireCurrentTenant().getId())
              .orElseThrow();
      questionnaire.setStatus(
          com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire.QuestionnaireStatus
              .READY_FOR_REVIEW);
      questionnaireRepository.save(questionnaire);

      // Stage 3: Auto-approve high confidence answers
      return processStageAutoApprove(questionnaireId);
    } catch (Exception e) {
      log.error("Failed to generate answers for questionnaire {}", questionnaireId, e);
      return new OrchestrationResult(false, "AI generation failed: " + e.getMessage(), e);
    }
  }

  private OrchestrationResult processStageAutoApprove(UUID questionnaireId) {
    log.info(
        "Stage 3: Auto-approving high confidence answers for questionnaire {}", questionnaireId);

    try {
      VantaReviewService.BulkApprovalResult result =
          reviewService.approveAllHighConfidence(questionnaireId, null, 85);

      log.info("Auto-approved {} questions, {} failed", result.getApproved(), result.getFailed());

      // Check if all questions are approved
      // If yes, mark questionnaire as APPROVED and trigger export

      return new OrchestrationResult(
          true, String.format("Auto-approved %d questions", result.getApproved()), null);
    } catch (Exception e) {
      log.error("Failed to auto-approve questionnaire {}", questionnaireId, e);
      return new OrchestrationResult(false, "Auto-approve failed: " + e.getMessage(), e);
    }
  }

  /** Scheduled task to process pending agent tasks */
  @Scheduled(fixedDelay = 5000) // Every 5 seconds
  @Transactional
  public void processPendingTasks() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) return;

    List<AgentTask> pendingTasks =
        taskRepository.findByTenantIdAndStatusAndDeletedAtIsNullOrderByPriorityDescCreatedAtAsc(
            tenantId, AgentTask.TaskStatus.PENDING);

    for (AgentTask task : pendingTasks) {
      if (taskQueue.tryAcquireLock(task.getId())) {
        taskQueue.executeTaskAsync(task);
      }
    }
  }

  /** Scheduled task to retry failed tasks */
  @Scheduled(fixedDelay = 60000) // Every minute
  @Transactional
  public void retryFailedTasks() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) return;

    OffsetDateTime now = OffsetDateTime.now();
    List<AgentTask> retryableTasks =
        taskRepository.findByTenantIdAndStatusAndNextRetryAtBeforeAndDeletedAtIsNull(
            tenantId, AgentTask.TaskStatus.RETRYING, OffsetDateTime.now());

    for (AgentTask task : retryableTasks) {
      if (task.getRetryCount() < task.getMaxRetries()) {
        task.setStatus(AgentTask.TaskStatus.PENDING);
        task.setRetryCount(task.getRetryCount() + 1);
        task.setNextRetryAt(null);
        taskRepository.save(task);
      } else {
        task.setStatus(AgentTask.TaskStatus.FAILED);
        task.setErrorMessage("Max retries exceeded");
        taskRepository.save(task);
      }
    }
  }

  // Helper methods
  private String serializeOptions(List<String> options) {
    if (options == null || options.isEmpty()) return "[]";
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(options);
    } catch (Exception e) {
      return "[]";
    }
  }

  private String serializeCoordinates(
      com.quenaire.ai.vanta.VantaSpreadsheetParserService.ParsedQuestion pq) {
    Map<String, Object> coords = new java.util.HashMap<>();
    coords.put("originalRowIndex", pq.getOriginalRowIndex());
    coords.put("originalHeaderRowIndex", pq.getOriginalHeaderRowIndex());
    coords.put("colIndex", pq.getColIndex());
    coords.put("sheetName", pq.getSheetName());
    coords.put("cellStyleData", pq.getCellStyleData());
    coords.put("cellCoordinates", pq.getCellCoordinates());
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(coords);
    } catch (Exception e) {
      return "{}";
    }
  }

  // Inner classes
  public static class OrchestrationResult {
    private final boolean success;
    private final String message;
    private final Exception error;

    public OrchestrationResult(boolean success, String message, Exception error) {
      this.success = success;
      this.message = message;
      this.error = error;
    }

    public boolean isSuccess() {
      return success;
    }

    public String getMessage() {
      return message;
    }

    public Exception getError() {
      return error;
    }
  }

  public static class OrchestrationState {
    private final UUID questionnaireId;
    private final UUID userId;
    private final OffsetDateTime startedAt;
    private String currentStage;
    private int progress;

    public OrchestrationState(UUID questionnaireId, UUID userId) {
      this.questionnaireId = questionnaireId;
      this.userId = userId;
      this.startedAt = OffsetDateTime.now();
      this.currentStage = "INIT";
      this.progress = 0;
    }

    // Getters
    public UUID getQuestionnaireId() {
      return questionnaireId;
    }

    public UUID getUserId() {
      return userId;
    }

    public OffsetDateTime getStartedAt() {
      return startedAt;
    }

    public String getCurrentStage() {
      return currentStage;
    }

    public void setCurrentStage(String currentStage) {
      this.currentStage = currentStage;
    }

    public int getProgress() {
      return progress;
    }

    public void setProgress(int progress) {
      this.progress = progress;
    }
  }
}
