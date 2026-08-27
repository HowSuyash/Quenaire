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
package com.complyai.api.rest.vanta;

import com.complyai.ai.vanta.AnswerLibraryService;
import com.complyai.domain.questionnaire.vanta.QuestionType;
import com.complyai.domain.questionnaire.vanta.VantaQuestion;
import com.complyai.domain.questionnaire.vanta.VantaQuestionRepository;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaire;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.complyai.domain.tenant.Tenant;
import com.complyai.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Receives answers captured by the browser extension on a third-party portal (OneTrust, Whistic,
 * Loopio, …) so work done in the vendor's own UI still lands in the answer library instead of being
 * lost.
 */
@RestController
@RequestMapping("/api/vanta/portal")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
public class PortalCaptureController {

  private static final Logger log = LoggerFactory.getLogger(PortalCaptureController.class);

  private final VantaQuestionnaireRepository questionnaireRepository;
  private final VantaQuestionRepository questionRepository;
  private final AnswerLibraryService answerLibraryService;
  private final ObjectMapper objectMapper;

  public PortalCaptureController(
      VantaQuestionnaireRepository questionnaireRepository,
      VantaQuestionRepository questionRepository,
      AnswerLibraryService answerLibraryService,
      ObjectMapper objectMapper) {
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
    this.answerLibraryService = answerLibraryService;
    this.objectMapper = objectMapper;
  }

  public record CapturedAnswer(
      String externalId,
      String questionText,
      String questionType,
      List<String> options,
      String answer,
      Integer confidenceScore) {}

  public record CaptureRequest(
      String platform,
      String sourceUrl,
      String pageTitle,
      String capturedAt,
      List<CapturedAnswer> answers) {}

  @PostMapping("/capture")
  @Transactional
  public ResponseEntity<Map<String, Object>> capture(@RequestBody CaptureRequest request) {
    Tenant tenant = TenantContext.getCurrentTenant();
    if (tenant == null) {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "No tenant context available"));
    }

    if (request == null || request.answers() == null || request.answers().isEmpty()) {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "No answers were submitted"));
    }

    UUID userId = TenantContext.getCurrentUserId();

    VantaQuestionnaire questionnaire = new VantaQuestionnaire();
    questionnaire.setTitle(titleFor(request));
    questionnaire.setDescription(
        "Captured by the ComplyAI browser extension from " + request.sourceUrl());
    questionnaire.setSourceType(sourceTypeFor(request.platform()));
    questionnaire.setSourceUrl(request.sourceUrl());
    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.IN_REVIEW);
    questionnaire.setTenant(tenant);
    questionnaire.setMetadataJson(metadataFor(request));
    questionnaire = questionnaireRepository.save(questionnaire);

    List<VantaQuestion> saved = new ArrayList<>();
    int rowIndex = 0;

    for (CapturedAnswer captured : request.answers()) {
      if (captured.questionText() == null || captured.questionText().isBlank()) {
        continue;
      }

      VantaQuestion question = new VantaQuestion();
      question.setQuestionnaire(questionnaire);
      question.setQuestionNumber(String.valueOf(++rowIndex));
      question.setText(captured.questionText());
      question.setOriginalText(captured.questionText());
      question.setQuestionType(mapQuestionType(captured.questionType()));
      question.setOptionsJson(serializeOptions(captured.options()));
      question.setAiAnswer(captured.answer());
      question.setFinalAnswer(captured.answer());
      question.setConfidenceScore(captured.confidenceScore());
      // Captured from a live portal by a human operator, so this is a human
      // edit awaiting approval rather than a fresh AI draft.
      question.setStatus(VantaQuestion.QuestionStatus.HUMAN_EDITED);
      question.setReviewedById(userId);
      question.setReviewedAt(OffsetDateTime.now());
      question.setRowIndex(rowIndex);
      question.setTenant(tenant);

      saved.add(questionRepository.save(question));
    }

    questionnaire.setTotalQuestions(saved.size());
    questionnaire.setAnsweredQuestions(saved.size());
    questionnaireRepository.save(questionnaire);

    log.info(
        "Captured {} portal answers from {} into questionnaire {}",
        saved.size(),
        request.platform(),
        questionnaire.getId());

    return ResponseEntity.ok(
        Map.of(
            "success",
            true,
            "questionnaireId",
            questionnaire.getId(),
            "captured",
            saved.size(),
            "message",
            "Captured "
                + saved.size()
                + " answers. Review and approve them to add them to your library."));
  }

  /**
   * Promote captured answers into the reusable answer library. Kept separate from capture so an
   * operator reviews them before they influence future questionnaires.
   */
  @PostMapping("/capture/{questionnaireId}/promote")
  @Transactional
  public ResponseEntity<Map<String, Object>> promoteToLibrary(@PathVariable UUID questionnaireId) {
    UUID userId = TenantContext.getCurrentUserId();

    List<VantaQuestion> questions =
        questionRepository.findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(
            questionnaireId);

    int promoted = 0;
    for (VantaQuestion question : questions) {
      String answer = question.getFinalAnswer();
      if (answer == null || answer.isBlank()) continue;

      try {
        answerLibraryService.createEntry(question, answer, userId);
        promoted++;
      } catch (Exception e) {
        log.warn("Could not promote question {} to the answer library", question.getId(), e);
      }
    }

    return ResponseEntity.ok(
        Map.of("success", true, "promoted", promoted, "total", questions.size()));
  }

  // ---- Helpers -------------------------------------------------------------

  private static String titleFor(CaptureRequest request) {
    if (request.pageTitle() != null && !request.pageTitle().isBlank()) {
      return request.pageTitle().length() > 250
          ? request.pageTitle().substring(0, 250)
          : request.pageTitle();
    }
    String platform = request.platform() != null ? request.platform() : "portal";
    return "Captured from " + platform;
  }

  private static VantaQuestionnaire.SourceType sourceTypeFor(String platform) {
    if (platform == null) return VantaQuestionnaire.SourceType.PORTAL_LINK;
    return switch (platform.toLowerCase()) {
      case "onetrust" -> VantaQuestionnaire.SourceType.ONETRUST;
      case "whistic" -> VantaQuestionnaire.SourceType.WHISTIC;
      case "loopio" -> VantaQuestionnaire.SourceType.LOOPIO;
      case "cybergrx" -> VantaQuestionnaire.SourceType.CYBERGRX;
      case "google_forms" -> VantaQuestionnaire.SourceType.GOOGLE_FORMS;
      case "typeform" -> VantaQuestionnaire.SourceType.TYPEFORM;
      default -> VantaQuestionnaire.SourceType.PORTAL_LINK;
    };
  }

  /** The extension reports DOM-derived types, not our enum names. */
  private static QuestionType mapQuestionType(String raw) {
    if (raw == null) return QuestionType.FREE_TEXT;
    return switch (raw.toLowerCase()) {
      case "radio", "single_choice" -> QuestionType.SINGLE_CHOICE;
      case "checkbox", "multiple_choice" -> QuestionType.MULTIPLE_CHOICE;
      case "select", "dropdown" -> QuestionType.SINGLE_CHOICE;
      case "yes_no", "boolean" -> QuestionType.YES_NO;
      case "number", "numeric" -> QuestionType.NUMERIC;
      case "date" -> QuestionType.DATE;
      case "file", "file_upload" -> QuestionType.FILE_UPLOAD;
      default -> QuestionType.FREE_TEXT;
    };
  }

  private String serializeOptions(List<String> options) {
    if (options == null || options.isEmpty()) return null;
    try {
      return objectMapper.writeValueAsString(options);
    } catch (Exception e) {
      return null;
    }
  }

  private String metadataFor(CaptureRequest request) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("capturedVia", "browser-extension");
    metadata.put("platform", request.platform());
    metadata.put("sourceUrl", request.sourceUrl());
    metadata.put("capturedAt", request.capturedAt());
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (Exception e) {
      return "{}";
    }
  }
}
