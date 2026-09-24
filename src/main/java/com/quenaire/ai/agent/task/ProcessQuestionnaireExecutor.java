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

import com.quenaire.ai.vanta.VantaQuestionnaireService;
import com.quenaire.ai.vanta.VantaSpreadsheetParserService;
import com.quenaire.domain.agent.AgentTask;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionRepository;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.quenaire.security.TenantContext;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessQuestionnaireExecutor implements AgentTaskExecutor {

  private static final Logger log = LoggerFactory.getLogger(ProcessQuestionnaireExecutor.class);

  private final VantaQuestionnaireService questionnaireService;
  private final VantaSpreadsheetParserService parserService;
  private final VantaQuestionnaireRepository questionnaireRepository;
  private final VantaQuestionRepository questionRepository;

  public ProcessQuestionnaireExecutor(
      VantaQuestionnaireService questionnaireService,
      VantaSpreadsheetParserService parserService,
      VantaQuestionnaireRepository questionnaireRepository,
      VantaQuestionRepository questionRepository) {
    this.questionnaireService = questionnaireService;
    this.parserService = parserService;
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
  }

  @Override
  public AgentTaskExecutionResult execute(AgentTask task) {
    log.info("Processing questionnaire task {}", task.getId());

    try {
      UUID questionnaireId = task.getQuestionnaireId();
      if (questionnaireId == null) {
        return new AgentTaskExecutionResult(false, null, "Missing questionnaireId in task payload");
      }

      Optional<com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire> questionnaireOpt =
          // Scoped by the task's own tenant rather than the thread-local one: this runs
          // on a queue worker, where no request has set a tenant context.
          questionnaireRepository.findByIdAndTenantIdAndDeletedAtIsNull(
              task.getQuestionnaireId(), task.getTenant().getId());

      if (questionnaireOpt.isEmpty()) {
        return new AgentTaskExecutionResult(false, null, "Questionnaire not found");
      }

      com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire questionnaire =
          questionnaireOpt.get();

      // Parse the spreadsheet if we have original file bytes
      if (questionnaire.getOriginalFileBytes() != null) {
        com.quenaire.ai.vanta.VantaSpreadsheetParserService.ParsedQuestionnaire parsed =
            parserService.parseBytes(
                questionnaire.getOriginalFileBytes(), questionnaire.getFileName());

        // Convert parsed questions to entities
        List<com.quenaire.domain.questionnaire.vanta.VantaQuestion> questions =
            new java.util.ArrayList<>();
        int globalRowIndex = 0;

        for (com.quenaire.ai.vanta.VantaSpreadsheetParserService.ParsedSheet sheet :
            parsed.getSheets()) {
          for (com.quenaire.ai.vanta.VantaSpreadsheetParserService.ParsedQuestion pq :
              sheet.getQuestions()) {
            com.quenaire.domain.questionnaire.vanta.VantaQuestion question =
                new com.quenaire.domain.questionnaire.vanta.VantaQuestion();
            question.setQuestionnaireId(task.getQuestionnaireId());
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

            questions.add(question);
          }
        }

        // Batch save questions
        // questionRepository.saveAll(questions);

        // Update questionnaire
        questionnaire.setTotalQuestions(questions.size());
        questionnaire.setStatus(
            com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire.QuestionnaireStatus
                .PROCESSING);
        // questionnaireRepository.save(questionnaire);
      }

      return new AgentTaskExecutionResult(
          true, "{\"status\": \"parsed\", \"questions\": " + 0 + "}", null);
    } catch (Exception e) {
      log.error("Failed to process questionnaire", e);
      return new AgentTaskExecutionResult(false, null, e.getMessage());
    }
  }

  private String serializeOptions(List<String> options) {
    if (options == null || options.isEmpty()) return "[]";
    try {
      return new tools.jackson.databind.ObjectMapper().writeValueAsString(options);
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
      return new tools.jackson.databind.ObjectMapper().writeValueAsString(coords);
    } catch (Exception e) {
      return "{}";
    }
  }

  @Override
  public AgentTask.TaskType getSupportedTaskType() {
    return AgentTask.TaskType.PROCESS_QUESTIONNAIRE;
  }
}
