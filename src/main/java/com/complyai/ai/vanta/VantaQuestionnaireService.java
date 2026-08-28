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

import com.complyai.domain.document.Document;
import com.complyai.domain.document.DocumentRepository;
import com.complyai.domain.questionnaire.vanta.*;
import com.complyai.security.TenantContext;
import java.io.IOException;
import java.util.*;
import java.util.Collection;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VantaQuestionnaireService {

  private static final Logger log = LoggerFactory.getLogger(VantaQuestionnaireService.class);

  /** Upper bound on a page the client can ask for. */
  private static final int MAX_PAGE_SIZE = 100;

  private final VantaSpreadsheetParserService parserService;
  private final VantaQuestionnaireRepository questionnaireRepository;
  private final VantaQuestionRepository questionRepository;
  private final AnswerLibraryService answerLibraryService;
  private final VantaReasoningEngine reasoningEngine;
  private final VantaReviewService reviewService;
  private final VantaExportService exportService;
  private final DocumentRepository documentRepository;

  public VantaQuestionnaireService(
      VantaSpreadsheetParserService parserService,
      VantaQuestionnaireRepository questionnaireRepository,
      VantaQuestionRepository questionRepository,
      AnswerLibraryService answerLibraryService,
      VantaReasoningEngine reasoningEngine,
      VantaReviewService reviewService,
      VantaExportService exportService,
      DocumentRepository documentRepository) {
    this.parserService = parserService;
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
    this.answerLibraryService = answerLibraryService;
    this.reasoningEngine = reasoningEngine;
    this.reviewService = reviewService;
    this.exportService = exportService;
    this.documentRepository = documentRepository;
  }

  @Transactional
  public VantaQuestionnaire uploadAndParse(MultipartFile file, String title, String description)
      throws IOException {
    log.info("Uploading and parsing questionnaire: {}", file.getOriginalFilename());

    // Parse the spreadsheet
    VantaSpreadsheetParserService.ParsedQuestionnaire parsed = parserService.parse(file);

    // Create questionnaire entity
    VantaQuestionnaire questionnaire = new VantaQuestionnaire();
    questionnaire.setTitle(title != null ? title : parsed.getFileName());
    questionnaire.setDescription(description);
    questionnaire.setSourceType(VantaQuestionnaire.SourceType.UPLOAD_EXCEL);
    questionnaire.setFileName(file.getOriginalFilename());
    questionnaire.setOriginalFileBytes(file.getBytes());
    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.PARSING);
    questionnaire.setTenantId(TenantContext.getCurrentTenantId());

    questionnaire = questionnaireRepository.save(questionnaire);

    // Convert parsed questions to entities
    List<VantaQuestion> questions = new ArrayList<>();
    int globalRowIndex = 0;

    for (VantaSpreadsheetParserService.ParsedSheet sheet : parsed.getSheets()) {
      for (VantaSpreadsheetParserService.ParsedQuestion pq : sheet.getQuestions()) {
        VantaQuestion question = new VantaQuestion();
        question.setQuestionnaire(questionnaire);
        question.setQuestionnaireId(questionnaire.getId());
        question.setQuestionNumber(pq.getQuestionNumber());
        question.setCategory(pq.getCategory());
        question.setSubCategory(pq.getSubCategory());
        question.setText(pq.getText());
        question.setQuestionType(pq.getQuestionType());
        question.setOptionsJson(serializeOptions(pq.getOptions()));
        question.setRequired(pq.getRequired());
        question.setAttachmentRequest(pq.getAttachmentRequest());
        question.setStatus(VantaQuestion.QuestionStatus.PENDING);
        question.setRowIndex(pq.getRowIndex());
        question.setColIndex(pq.getColIndex());
        question.setSheetName(pq.getSheetName());
        question.setOriginalRowIndex(pq.getOriginalRowIndex());
        question.setOriginalHeaderRowIndex(pq.getOriginalHeaderRowIndex());
        question.setMetadataJson(serializeCoordinates(pq));
        question.setTenantId(TenantContext.getCurrentTenantId());

        questions.add(question);
        globalRowIndex++;
      }
    }

    // Batch save questions
    questionRepository.saveAll(questions);
    questionnaire.getQuestions().addAll(questions);

    // Update counts
    questionnaire.setTotalQuestions(questions.size());
    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.PROCESSING);
    questionnaire = questionnaireRepository.save(questionnaire);

    log.info("Parsed {} questions from {} sheets", questions.size(), parsed.getTotalSheets());
    return questionnaire;
  }

  /**
   * A questionnaire belonging to the caller's tenant.
   *
   * <p>Every one of these lookups used {@code findById}, which is not scoped to anything. There is
   * no Hibernate tenant filter in this application -- isolation is only ever whatever the query
   * says -- so an id from one customer's URL fetched another customer's questionnaire, and the
   * export path handed back the original uploaded file with it. The scoped finder already existed;
   * it was simply not the one being called.
   *
   * <p>A questionnaire belonging to someone else is reported as missing rather than forbidden, so
   * the caller learns nothing about whether the id exists.
   */
  private VantaQuestionnaire requireOwned(UUID questionnaireId) {
    return questionnaireRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(
            questionnaireId, TenantContext.requireCurrentTenant().getId())
        .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));
  }

  /**
   * The tenant's questionnaires, newest first.
   *
   * <p>The controller returned an empty list with a TODO where this call belongs, so the product's
   * main listing screen showed nothing however many questionnaires had been uploaded.
   */
  @Transactional(readOnly = true)
  public Page<VantaQuestionnaire> listQuestionnaires(int page, int size) {
    // A caller asking for ten thousand rows should not be able to ask the database for them.
    int bounded = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    return questionnaireRepository.findByTenantIdAndDeletedAtIsNull(
        TenantContext.requireCurrentTenant().getId(),
        PageRequest.of(Math.max(page, 0), bounded, Sort.by(Sort.Direction.DESC, "createdAt")));
  }

  /**
   * Resolves document ids from a request and processes against them.
   *
   * <p>An id naming another tenant's document is dropped rather than refused: the caller learns
   * nothing about whether it exists.
   */
  @Transactional
  public VantaQuestionnaire processQuestionnaireWithDocumentIds(
      UUID questionnaireId, Collection<UUID> documentIds) {
    List<Document> documents =
        documentIds == null || documentIds.isEmpty()
            ? List.of()
            : documentRepository.findByTenantIdAndIdInAndDeletedAtIsNull(
                TenantContext.requireCurrentTenant().getId(), documentIds);

    if (documentIds != null && documents.size() != documentIds.size()) {
      log.warn(
          "Processing questionnaire {} against {} of {} requested documents; the rest are not"
              + " this tenant's or no longer exist",
          questionnaireId,
          documents.size(),
          documentIds.size());
    }

    return processQuestionnaire(questionnaireId, documents);
  }

  @Transactional
  public VantaQuestionnaire processQuestionnaire(UUID questionnaireId, List<Document> documents) {
    log.info("Processing questionnaire {} with {} documents", questionnaireId, documents.size());

    VantaQuestionnaire questionnaire = requireOwned(questionnaireId);

    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.PROCESSING);
    questionnaireRepository.save(questionnaire);

    List<VantaQuestion> questions =
        questionRepository.findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(
            questionnaireId);

    int processed = 0;
    int highConfidence = 0;

    for (VantaQuestion question : questions) {
      // Run AI reasoning
      VantaReasoningEngine.ReasoningResult result = reasoningEngine.reason(question, documents);

      // Update question with AI answer
      question.setAiAnswer(result.getAnswer());
      question.setConfidenceScore(result.getConfidenceScore());
      question.setSelectedOption(result.getSelectedOption());

      // Store citations
      if (result.getCitations() != null) {
        List<UUID> citationIds =
            result.getCitations().stream().map(Citation::getId).collect(Collectors.toList());
        question.setCitationIds(citationIds);
      }

      // Determine initial status based on confidence
      if (result.getConfidenceLevel() == ConfidenceLevel.HIGH) {
        question.setStatus(VantaQuestion.QuestionStatus.AI_DRAFTED);
        highConfidence++;
      } else if (result.getConfidenceLevel() == ConfidenceLevel.MEDIUM) {
        question.setStatus(VantaQuestion.QuestionStatus.NEEDS_REVIEW);
      } else {
        question.setStatus(VantaQuestion.QuestionStatus.NEEDS_REVIEW);
      }

      questionRepository.save(question);
      processed++;
    }

    // Update questionnaire stats
    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.READY_FOR_REVIEW);
    questionnaire.setAnsweredQuestions(processed);
    questionnaire.setHighConfidenceQuestions(highConfidence);
    questionnaire.setNeedsReviewQuestions(processed - highConfidence);
    questionnaireRepository.save(questionnaire);

    log.info(
        "Processed {} questions: {} high confidence, {} need review",
        processed,
        highConfidence,
        processed - highConfidence);
    return questionnaire;
  }

  @Transactional
  public VantaReviewService.BulkApprovalResult approveAllHighConfidence(
      UUID questionnaireId, UUID approvedById, int threshold) {
    return reviewService.approveAllHighConfidence(questionnaireId, approvedById, threshold);
  }

  public byte[] exportToOriginalExcel(UUID questionnaireId) throws IOException {
    VantaQuestionnaire questionnaire = requireOwned(questionnaireId);

    return exportService.exportToOriginalExcel(questionnaire, questionnaire.getOriginalFileBytes());
  }

  public byte[] exportToNewExcel(UUID questionnaireId) throws IOException {
    VantaQuestionnaire questionnaire = requireOwned(questionnaireId);

    return exportService.exportToNewExcel(questionnaire);
  }

  public byte[] exportExecutiveSummary(UUID questionnaireId) throws IOException {
    VantaQuestionnaire questionnaire = requireOwned(questionnaireId);

    return exportService.exportExecutiveSummary(questionnaire);
  }

  @Transactional
  public VantaQuestion approveQuestion(UUID questionId, UUID approvedById, String notes) {
    return reviewService.approveQuestion(questionId, approvedById, notes);
  }

  @Transactional
  public VantaQuestion rejectQuestion(UUID questionId, UUID rejectedById, String reason) {
    return reviewService.rejectQuestion(questionId, rejectedById, reason);
  }

  @Transactional
  public VantaQuestion delegateQuestion(
      UUID questionId, UUID delegatedById, UUID assignedToId, String notes) {
    return reviewService.delegateQuestion(questionId, delegatedById, assignedToId, notes);
  }

  @Transactional
  public VantaQuestion updateAnswer(
      UUID questionId, UUID updatedById, String answer, String notes) {
    return reviewService.updateAnswer(questionId, updatedById, answer, notes);
  }

  @Transactional
  public VantaQuestion selectOption(UUID questionId, UUID updatedById, String option) {
    return reviewService.selectOption(questionId, updatedById, option);
  }

  public VantaReviewService.ReviewStats getReviewStats(UUID questionnaireId) {
    return reviewService.getReviewStats(questionnaireId);
  }

  public List<VantaQuestion> getQuestions(UUID questionnaireId) {
    return questionRepository.findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(
        questionnaireId);
  }

  public Optional<VantaQuestion> getQuestion(UUID questionId) {
    return questionRepository.findByIdAndTenantIdAndDeletedAtIsNull(
        questionId, TenantContext.getCurrentTenantId());
  }

  private String serializeOptions(List<String> options) {
    if (options == null || options.isEmpty()) return "[]";
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(options);
    } catch (Exception e) {
      return "[]";
    }
  }

  private String serializeCoordinates(VantaSpreadsheetParserService.ParsedQuestion pq) {
    Map<String, Object> coords = new HashMap<>();
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

  public Optional<VantaQuestionnaire> getQuestionnaire(UUID id) {
    return questionnaireRepository.findByIdAndTenantIdAndDeletedAtIsNull(
        id, TenantContext.requireCurrentTenant().getId());
  }
}
