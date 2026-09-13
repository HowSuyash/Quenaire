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
package com.quenaire.api.rest.vanta;

import com.quenaire.ai.vanta.VantaQuestionnaireService;
import com.quenaire.ai.vanta.VantaReviewService;
import com.quenaire.api.rest.dto.PageResponse;
import com.quenaire.api.rest.vanta.dto.QuestionResponse;
import com.quenaire.api.rest.vanta.dto.QuestionnaireResponse;
import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.security.TenantContext;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vanta/questionnaires")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
public class VantaQuestionnaireController {

  private static final Logger log = LoggerFactory.getLogger(VantaQuestionnaireController.class);

  private final VantaQuestionnaireService questionnaireService;
  private final VantaReviewService reviewService;

  public VantaQuestionnaireController(
      VantaQuestionnaireService questionnaireService, VantaReviewService reviewService) {
    this.questionnaireService = questionnaireService;
    this.reviewService = reviewService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> uploadQuestionnaire(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "title", required = false) String title,
      @RequestParam(value = "description", required = false) String description) {

    try {
      VantaQuestionnaire questionnaire =
          questionnaireService.uploadAndParse(file, title, description);
      return ResponseEntity.ok(
          Map.of(
              "success", true,
              "questionnaireId", questionnaire.getId(),
              "title", questionnaire.getTitle(),
              "totalQuestions", questionnaire.getTotalQuestions(),
              "status", questionnaire.getStatus().name()));
    } catch (IOException e) {
      log.error("Failed to upload questionnaire", e);
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "success", false, "error", "Failed to parse questionnaire: " + e.getMessage()));
    }
  }

  /**
   * The tenant's questionnaires.
   *
   * <p>This returned an empty list unconditionally, so the listing screen was blank however many
   * questionnaires had been uploaded -- a 200 that looked like "you have none".
   */
  @GetMapping
  public ResponseEntity<PageResponse<QuestionnaireResponse>> listQuestionnaires(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

    return ResponseEntity.ok(
        PageResponse.of(
            questionnaireService.listQuestionnaires(page, size), QuestionnaireResponse::of));
  }

  @GetMapping("/{id}")
  public ResponseEntity<QuestionnaireResponse> getQuestionnaire(@PathVariable UUID id) {
    return questionnaireService
        .getQuestionnaire(id)
        .map(QuestionnaireResponse::of)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/{id}/process")
  public ResponseEntity<Map<String, Object>> processQuestionnaire(
      @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> request) {

    // The selected documents were parsed out of the request and then thrown away: every
    // questionnaire was answered against the whole corpus regardless of what the customer
    // chose. Choosing "answer from the SOC 2 report only" did nothing.
    List<UUID> documentIds;
    try {
      documentIds =
          request == null
              ? List.of()
              : ((List<?>) request.getOrDefault("documentIds", List.of()))
                  .stream().map(value -> UUID.fromString(String.valueOf(value))).toList();
    } catch (IllegalArgumentException | ClassCastException e) {
      // There is no @ControllerAdvice in this application, so an unhandled parse failure
      // here would surface as a 500 -- a server fault for what is a malformed request.
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "documentIds must be a list of UUIDs"));
    }

    VantaQuestionnaire processed =
        questionnaireService.processQuestionnaireWithDocumentIds(id, documentIds);

    return ResponseEntity.ok(
        Map.of(
            "success",
            true,
            "questionnaire",
            QuestionnaireResponse.of(processed),
            "stats",
            Map.of(
                "totalQuestions", processed.getTotalQuestions(),
                "highConfidence", processed.getHighConfidenceQuestions(),
                "needsReview", processed.getNeedsReviewQuestions())));
  }

  @PostMapping("/{id}/approve-all-high-confidence")
  public ResponseEntity<Map<String, Object>> approveAllHighConfidence(
      @PathVariable UUID id, @RequestParam(defaultValue = "90") int threshold) {

    VantaReviewService.BulkApprovalResult result =
        questionnaireService.approveAllHighConfidence(
            id, TenantContext.getCurrentUserId(), threshold);

    return ResponseEntity.ok(
        Map.of(
            "success", true,
            "approved", result.getApproved(),
            "failed", result.getFailed(),
            "approvedIds", result.getApprovedIds(),
            "errors", result.getErrors()));
  }

  @GetMapping("/{id}/stats")
  public ResponseEntity<VantaReviewService.ReviewStats> getReviewStats(@PathVariable UUID id) {
    VantaReviewService.ReviewStats stats = questionnaireService.getReviewStats(id);
    return ResponseEntity.ok(stats);
  }

  @GetMapping("/{id}/questions")
  public ResponseEntity<List<QuestionResponse>> getQuestions(@PathVariable UUID id) {
    return ResponseEntity.ok(
        questionnaireService.getQuestions(id).stream().map(QuestionResponse::of).toList());
  }

  @GetMapping("/questions/{questionId}")
  public ResponseEntity<QuestionResponse> getQuestion(@PathVariable UUID questionId) {
    return questionnaireService
        .getQuestion(questionId)
        .map(QuestionResponse::of)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/questions/{questionId}/approve")
  public ResponseEntity<Map<String, Object>> approveQuestion(
      @PathVariable UUID questionId, @RequestBody Map<String, Object> request) {

    String notes = (String) request.getOrDefault("notes", "Approved via API");
    VantaQuestion question =
        questionnaireService.approveQuestion(questionId, TenantContext.getCurrentUserId(), notes);

    return ResponseEntity.ok(Map.of("success", true, "question", question));
  }

  @PostMapping("/questions/{questionId}/reject")
  public ResponseEntity<Map<String, Object>> rejectQuestion(
      @PathVariable UUID questionId, @RequestBody Map<String, Object> request) {

    String reason = (String) request.getOrDefault("reason", "Rejected via API");
    VantaQuestion question =
        questionnaireService.rejectQuestion(questionId, TenantContext.getCurrentUserId(), reason);

    return ResponseEntity.ok(Map.of("success", true, "question", question));
  }

  @PostMapping("/questions/{questionId}/delegate")
  public ResponseEntity<Map<String, Object>> delegateQuestion(
      @PathVariable UUID questionId, @RequestBody Map<String, Object> request) {

    UUID assignedToId = UUID.fromString((String) request.get("assignedToId"));
    String notes = (String) request.getOrDefault("notes", "Delegated via API");

    VantaQuestion question =
        questionnaireService.delegateQuestion(
            questionId, TenantContext.getCurrentUserId(), assignedToId, notes);

    return ResponseEntity.ok(Map.of("success", true, "question", question));
  }

  @PostMapping("/questions/{questionId}/answer")
  public ResponseEntity<Map<String, Object>> updateAnswer(
      @PathVariable UUID questionId, @RequestBody Map<String, Object> request) {

    String answer = (String) request.get("answer");
    String notes = (String) request.getOrDefault("notes", "Updated via API");

    VantaQuestion question =
        questionnaireService.updateAnswer(
            questionId, TenantContext.getCurrentUserId(), answer, notes);

    return ResponseEntity.ok(Map.of("success", true, "question", question));
  }

  @PostMapping("/questions/{questionId}/select-option")
  public ResponseEntity<Map<String, Object>> selectOption(
      @PathVariable UUID questionId, @RequestBody Map<String, Object> request) {

    String option = (String) request.get("option");

    VantaQuestion question =
        questionnaireService.selectOption(questionId, TenantContext.getCurrentUserId(), option);

    return ResponseEntity.ok(Map.of("success", true, "question", question));
  }

  @GetMapping("/{id}/export/original")
  public ResponseEntity<Resource> exportOriginalExcel(@PathVariable UUID id) throws IOException {
    byte[] bytes = questionnaireService.exportToOriginalExcel(id);

    VantaQuestionnaire questionnaire =
        questionnaireService
            .getQuestionnaire(id)
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    String filename =
        (questionnaire.getFileName() != null ? questionnaire.getFileName() : "questionnaire")
                .replace(".xlsx", "")
            + "_filled.xlsx";

    ByteArrayResource resource = new ByteArrayResource(bytes);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .contentLength(bytes.length)
        .body(resource);
  }

  @GetMapping("/{id}/export/formatted")
  public ResponseEntity<Resource> exportFormattedExcel(@PathVariable UUID id) throws IOException {
    byte[] bytes = questionnaireService.exportToNewExcel(id);

    VantaQuestionnaire questionnaire =
        questionnaireService
            .getQuestionnaire(id)
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    String filename =
        (questionnaire.getTitle() != null ? questionnaire.getTitle() : "questionnaire")
                .replaceAll("[^a-zA-Z0-9]", "_")
            + "_formatted.xlsx";

    ByteArrayResource resource = new ByteArrayResource(bytes);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .contentLength(bytes.length)
        .body(resource);
  }

  @GetMapping("/{id}/export/summary")
  public ResponseEntity<Resource> exportExecutiveSummary(@PathVariable UUID id) throws IOException {
    byte[] bytes = questionnaireService.exportExecutiveSummary(id);

    VantaQuestionnaire questionnaire =
        questionnaireService
            .getQuestionnaire(id)
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    String filename =
        (questionnaire.getTitle() != null ? questionnaire.getTitle() : "questionnaire")
                .replaceAll("[^a-zA-Z0-9]", "_")
            + "_executive_summary.xlsx";

    ByteArrayResource resource = new ByteArrayResource(bytes);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .contentLength(bytes.length)
        .body(resource);
  }

  @PostMapping("/questions/{questionId}/reason")
  public ResponseEntity<Map<String, Object>> reasonQuestion(@PathVariable UUID questionId) {
    // This would trigger AI reasoning for a specific question
    return ResponseEntity.ok(
        Map.of("message", "Reasoning endpoint - implement with VantaReasoningEngine"));
  }
}
