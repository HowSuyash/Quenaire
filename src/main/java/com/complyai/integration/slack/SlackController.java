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
package com.complyai.integration.slack;

import com.complyai.domain.document.Document;
import com.complyai.domain.document.DocumentRepository;
import com.complyai.domain.questionnaire.Question;
import com.complyai.domain.questionnaire.QuestionRepository;
import com.complyai.domain.questionnaire.Questionnaire;
import com.complyai.domain.questionnaire.QuestionnaireRepository;
import com.complyai.security.TenantContext;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/slack")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
public class SlackController {

  private final SlackIntegrationService slackService;
  private final QuestionnaireRepository questionnaireRepository;
  private final QuestionRepository questionRepository;
  private final DocumentRepository documentRepository;

  public SlackController(
      SlackIntegrationService slackService,
      QuestionnaireRepository questionnaireRepository,
      QuestionRepository questionRepository,
      DocumentRepository documentRepository) {
    this.slackService = slackService;
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
    this.documentRepository = documentRepository;
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getStatus() {
    return ResponseEntity.ok(
        Map.of(
            "configured",
            slackService.isConfigured(),
            "defaultChannel",
            slackService.isConfigured() ? "configured" : "not configured"));
  }

  @PostMapping("/questionnaires/{questionnaireId}/notify")
  public ResponseEntity<Map<String, Object>> notifyQuestionnaire(
      @PathVariable UUID questionnaireId, @RequestParam(required = false) String channel) {
    Questionnaire questionnaire =
        questionnaireRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(
                questionnaireId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    Optional<String> ts = slackService.sendQuestionnaireNotification(questionnaire, channel);
    if (ts.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "ts", ts.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to send notification"));
  }

  @PostMapping("/questions/{questionId}/approval-request")
  public ResponseEntity<Map<String, Object>> requestApproval(
      @PathVariable UUID questionId,
      @RequestParam String answer,
      @RequestParam(required = false) String channel) {
    Question question =
        questionRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(questionId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));

    Optional<String> ts = slackService.sendQuestionApprovalRequest(question, answer, channel);
    if (ts.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "ts", ts.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to send approval request"));
  }

  @PostMapping("/questionnaires/{questionnaireId}/complete")
  public ResponseEntity<Map<String, Object>> notifyComplete(
      @PathVariable UUID questionnaireId,
      @RequestParam int approvedCount,
      @RequestParam int totalCount,
      @RequestParam(required = false) String channel) {
    Questionnaire questionnaire =
        questionnaireRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(
                questionnaireId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    Optional<String> ts =
        slackService.sendQuestionnaireCompleteNotification(
            questionnaire, approvedCount, totalCount, channel);
    if (ts.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "ts", ts.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to send notification"));
  }

  @PostMapping("/documents/{documentId}/notify")
  public ResponseEntity<Map<String, Object>> notifyDocument(
      @PathVariable UUID documentId, @RequestParam(required = false) String channel) {
    Document document =
        documentRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(documentId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));

    Optional<String> ts = slackService.sendDocumentIndexedNotification(document, channel);
    if (ts.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "ts", ts.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to send notification"));
  }

  @PostMapping("/alert")
  public ResponseEntity<Map<String, Object>> sendAlert(@RequestBody AlertRequest request) {
    Optional<String> ts =
        slackService.sendAlert(
            request.title(), request.message(), request.level(), request.channel());
    if (ts.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "ts", ts.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to send alert"));
  }

  @PostMapping("/message")
  public ResponseEntity<Map<String, Object>> sendCustomMessage(
      @RequestBody MessageRequest request) {
    Optional<String> ts = slackService.sendMessage(request.channel(), request.text());
    if (ts.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "ts", ts.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to send message"));
  }

  public record AlertRequest(String title, String message, String level, String channel) {}

  public record MessageRequest(String channel, String text) {}
}
