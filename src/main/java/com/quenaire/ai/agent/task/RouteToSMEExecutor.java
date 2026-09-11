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
package com.quenaire.ai.agent.task;

import com.quenaire.ai.agent.ConfidenceRouter;
import com.quenaire.ai.agent.SMERoutingService;
import com.quenaire.domain.agent.AgentTask;
import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionRepository;
import com.quenaire.security.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RouteToSMEExecutor implements AgentTaskExecutor {

  private static final Logger log = LoggerFactory.getLogger(RouteToSMEExecutor.class);

  private final ConfidenceRouter confidenceRouter;
  private final SMERoutingService smeRoutingService;
  private final VantaQuestionRepository questionRepository;
  private final com.quenaire.domain.questionnaire.vanta.VantaQuestionnaireRepository
      questionnaireRepository;

  public RouteToSMEExecutor(
      ConfidenceRouter confidenceRouter,
      SMERoutingService smeRoutingService,
      VantaQuestionRepository questionRepository,
      com.quenaire.domain.questionnaire.vanta.VantaQuestionnaireRepository
          questionnaireRepository) {
    this.confidenceRouter = confidenceRouter;
    this.smeRoutingService = smeRoutingService;
    this.questionRepository = questionRepository;
    this.questionnaireRepository = questionnaireRepository;
  }

  @Override
  @Transactional
  public AgentTaskExecutionResult execute(AgentTask task) {
    log.info("Routing to SME task {}", task.getId());

    try {
      UUID questionId = task.getQuestionId();
      UUID questionnaireId = task.getQuestionnaireId();

      if (questionId == null) {
        // Route all questions in questionnaire that need SME review
        if (questionnaireId == null) {
          return new AgentTaskExecutionResult(false, null, "Missing questionnaireId or questionId");
        }

        List<VantaQuestion> questions =
            questionRepository.findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(
                questionnaireId);

        int routed = 0;
        int failed = 0;

        for (VantaQuestion question : questions) {
          if (question.getStatus() == VantaQuestion.QuestionStatus.NEEDS_REVIEW
              || question.getStatus() == VantaQuestion.QuestionStatus.AI_DRAFTED) {

            ConfidenceRouter.RoutingDecision decision = confidenceRouter.route(question);

            if (decision.decision() == ConfidenceRouter.RouteDecision.SME_QUEUE) {
              SMERoutingService.RoutingResult result =
                  smeRoutingService.routeToSME(
                      question.getId(), questionnaireId, question.getCategory(), null);

              if (result.success()) {
                routed++;
                // Update question status
                question.setStatus(VantaQuestion.QuestionStatus.DELEGATED);
                question.setAssignedToId(result.smeId());
                questionRepository.save(question);
              } else {
                failed++;
              }
            }
          }
        }

        return new AgentTaskExecutionResult(
            true, String.format("{\"routed\":%d,\"failed\":%d}", routed, failed), null);
      } else {
        // Route single question
        Optional<VantaQuestion> questionOpt =
            questionRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                questionId, TenantContext.getCurrentTenantId());

        if (questionOpt.isEmpty()) {
          return new AgentTaskExecutionResult(false, null, "Question not found");
        }

        VantaQuestion question = questionOpt.get();
        ConfidenceRouter.RoutingDecision decision = confidenceRouter.route(question);

        if (decision.decision() == ConfidenceRouter.RouteDecision.SME_QUEUE) {
          SMERoutingService.RoutingResult result =
              smeRoutingService.routeToSME(
                  questionId, questionnaireId, question.getCategory(), null);

          if (result.success()) {
            question.setStatus(VantaQuestion.QuestionStatus.DELEGATED);
            question.setAssignedToId(result.smeId());
            questionRepository.save(question);

            return new AgentTaskExecutionResult(
                true, String.format("{\"routed\":true,\"smeId\":\"%s\"}", result.smeId()), null);
          } else {
            return new AgentTaskExecutionResult(false, null, result.message());
          }
        } else {
          return new AgentTaskExecutionResult(
              false, null, "Question does not require SME routing: " + decision.reason());
        }
      }
    } catch (Exception e) {
      log.error("Failed to route to SME", e);
      return new AgentTaskExecutionResult(false, null, e.getMessage());
    }
  }

  @Override
  public AgentTask.TaskType getSupportedTaskType() {
    return AgentTask.TaskType.ROUTE_TO_SME;
  }
}
