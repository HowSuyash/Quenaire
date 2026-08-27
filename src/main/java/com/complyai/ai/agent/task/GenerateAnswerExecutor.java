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
package com.complyai.ai.agent.task;

import com.complyai.ai.vanta.VantaReasoningEngine;
import com.complyai.ai.vanta.VantaReviewService;
import com.complyai.domain.agent.AgentTask;
import com.complyai.domain.questionnaire.vanta.ConfidenceLevel;
import com.complyai.domain.questionnaire.vanta.VantaQuestionRepository;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GenerateAnswerExecutor implements AgentTaskExecutor {

  private static final Logger log = LoggerFactory.getLogger(GenerateAnswerExecutor.class);

  private final VantaReasoningEngine reasoningEngine;
  private final VantaQuestionRepository questionRepository;
  private final VantaQuestionnaireRepository questionnaireRepository;
  private final VantaReviewService reviewService;

  public GenerateAnswerExecutor(
      VantaReasoningEngine reasoningEngine,
      VantaQuestionRepository questionRepository,
      VantaQuestionnaireRepository questionnaireRepository,
      VantaReviewService reviewService) {
    this.reasoningEngine = reasoningEngine;
    this.questionRepository = questionRepository;
    this.questionnaireRepository = questionnaireRepository;
    this.reviewService = reviewService;
  }

  @Override
  @Transactional
  public AgentTaskExecutionResult execute(AgentTask task) {
    log.info("Generating answers task {}", task.getId());

    try {
      UUID questionnaireId = task.getQuestionnaireId();
      if (questionnaireId == null) {
        return new AgentTaskExecutionResult(false, null, "Missing questionnaireId");
      }

      List<com.complyai.domain.questionnaire.vanta.VantaQuestion> questions =
          questionRepository.findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(
              questionnaireId);

      if (questions.isEmpty()) {
        return new AgentTaskExecutionResult(false, null, "No questions found for questionnaire");
      }

      // Fetch documents for RAG (in production, fetch from document repository)
      List<com.complyai.domain.document.Document> documents = new ArrayList<>();

      int processed = 0;
      int highConfidence = 0;
      int needsReview = 0;

      for (com.complyai.domain.questionnaire.vanta.VantaQuestion question : questions) {
        // Skip if already has AI answer
        if (question.getAiAnswer() != null && !question.getAiAnswer().isEmpty()) {
          continue;
        }

        // Run AI reasoning
        com.complyai.ai.vanta.VantaReasoningEngine.ReasoningResult result =
            reasoningEngine.reason(question, new ArrayList<>());

        // Update question with AI answer
        question.setAiAnswer(result.getAnswer());
        question.setConfidenceScore(result.getConfidenceScore());
        question.setSelectedOption(result.getSelectedOption());

        // Store citations
        if (result.getCitations() != null) {
          List<UUID> citationIds =
              result.getCitations().stream()
                  .map(com.complyai.domain.questionnaire.vanta.Citation::getId)
                  .collect(Collectors.toList());
          question.setCitationIds(citationIds);
        }

        // Determine initial status based on confidence
        if (result.getConfidenceLevel() == ConfidenceLevel.HIGH) {
          question.setStatus(
              com.complyai.domain.questionnaire.vanta.VantaQuestion.QuestionStatus.AI_DRAFTED);
          highConfidence++;
        } else if (result.getConfidenceLevel() == ConfidenceLevel.MEDIUM) {
          question.setStatus(
              com.complyai.domain.questionnaire.vanta.VantaQuestion.QuestionStatus.NEEDS_REVIEW);
          needsReview++;
        } else {
          question.setStatus(
              com.complyai.domain.questionnaire.vanta.VantaQuestion.QuestionStatus.NEEDS_REVIEW);
          needsReview++;
        }

        questionRepository.save(question);
        processed++;
      }

      // Update questionnaire stats
      com.complyai.domain.questionnaire.vanta.VantaQuestionnaire questionnaire =
          questionnaireRepository.findById(task.getQuestionnaireId()).orElseThrow();

      questionnaire.setStatus(
          com.complyai.domain.questionnaire.vanta.VantaQuestionnaire.QuestionnaireStatus
              .READY_FOR_REVIEW);
      questionnaire.setAnsweredQuestions(processed);
      questionnaire.setHighConfidenceQuestions(highConfidence);
      questionnaire.setNeedsReviewQuestions(needsReview);
      // questionnaireRepository.save(questionnaire);

      // Auto-approve high confidence
      // reviewService.approveAllHighConfidence(questionnaireId, null, 85);

      String resultJson =
          String.format(
              "{\"processed\":%d,\"highConfidence\":%d,\"needsReview\":%d}",
              processed, highConfidence, needsReview);

      return new AgentTaskExecutionResult(true, resultJson, null);
    } catch (Exception e) {
      log.error("Failed to generate answers", e);
      return new AgentTaskExecutionResult(false, null, e.getMessage());
    }
  }

  @Override
  public AgentTask.TaskType getSupportedTaskType() {
    return AgentTask.TaskType.GENERATE_ANSWER;
  }
}
