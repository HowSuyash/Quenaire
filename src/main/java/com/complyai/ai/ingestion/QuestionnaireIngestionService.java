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
package com.complyai.ai.ingestion;

import com.complyai.domain.questionnaire.Question;
import com.complyai.domain.questionnaire.QuestionRepository;
import com.complyai.domain.questionnaire.QuestionStatus;
import com.complyai.domain.questionnaire.Questionnaire;
import com.complyai.domain.questionnaire.QuestionnaireRepository;
import com.complyai.domain.questionnaire.QuestionnaireSourceType;
import com.complyai.domain.questionnaire.QuestionnaireStatus;
import com.complyai.domain.tenant.TenantRepository;
import com.complyai.security.TenantContext;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionnaireIngestionService {

  private final QuestionnaireRepository questionnaireRepository;
  private final QuestionRepository questionRepository;
  private final QuestionnaireParserService parserService;
  private final TenantRepository tenantRepository;

  public QuestionnaireIngestionService(
      QuestionnaireRepository questionnaireRepository,
      QuestionRepository questionRepository,
      QuestionnaireParserService parserService,
      TenantRepository tenantRepository) {
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
    this.parserService = parserService;
    this.tenantRepository = tenantRepository;
  }

  @Transactional
  public Questionnaire ingest(MultipartFile file, QuestionnaireSourceType sourceType)
      throws IOException {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    var parsed = parserService.parse(file);

    Questionnaire questionnaire = new Questionnaire();
    questionnaire.setTitle(parsed.fileName());
    questionnaire.setSourceType(sourceType);
    questionnaire.setFileName(parsed.fileName());
    questionnaire.setStatus(QuestionnaireStatus.PROCESSING);
    questionnaire.setTotalQuestions(parsed.questions().size());
    questionnaire.setTenant(tenantRepository.getReferenceById(tenantId));
    questionnaire.setCreatedById(getCurrentUserId());

    questionnaire = questionnaireRepository.save(questionnaire);

    try {
      processQuestions(questionnaire, parsed.questions());
      questionnaire.setStatus(QuestionnaireStatus.READY_FOR_REVIEW);
      questionnaireRepository.save(questionnaire);
    } catch (Exception e) {
      questionnaire.setStatus(QuestionnaireStatus.DRAFT);
      questionnaireRepository.save(questionnaire);
      throw new IngestionException("Failed to process questionnaire", e);
    }

    return questionnaire;
  }

  private void processQuestions(
      Questionnaire questionnaire,
      List<QuestionnaireParserService.ParsedQuestion> parsedQuestions) {
    int index = 0;
    for (var parsed : parsedQuestions) {
      Question question = new Question();
      question.setQuestionnaire(questionnaire);
      question.setText(parsed.text());
      question.setQuestionType(parsed.type());
      question.setOptionsJson(toJsonArray(parsed.options()));
      question.setSection(parsed.section());
      question.setSubsection(parsed.subsection());
      question.setQuestionNumber(parsed.questionNumber());
      question.setRowIndex(index++);
      question.setStatus(QuestionStatus.PENDING);
      question.setIsRequired(parsed.required());
      question.setTagsJson(toJsonArray(parsed.tags()));
      question.setTenant(questionnaire.getTenant());

      questionRepository.save(question);
    }
  }

  private String toJsonArray(List<String> items) {
    if (items == null || items.isEmpty()) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append('"').append(escapeJson(items.get(i))).append('"');
    }
    sb.append("]");
    return sb.toString();
  }

  private String escapeJson(String s) {
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private UUID getCurrentUserId() {
    return null;
  }

  public static class IngestionException extends RuntimeException {
    public IngestionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
