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
package com.quenaire.integration.jira;

import com.quenaire.domain.questionnaire.Question;
import com.quenaire.domain.questionnaire.QuestionRepository;
import com.quenaire.domain.questionnaire.Questionnaire;
import com.quenaire.domain.questionnaire.QuestionnaireRepository;
import com.quenaire.security.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/jira")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
public class JiraController {

  private final JiraIntegrationService jiraService;
  private final QuestionnaireRepository questionnaireRepository;
  private final QuestionRepository questionRepository;

  public JiraController(
      JiraIntegrationService jiraService,
      QuestionnaireRepository questionnaireRepository,
      QuestionRepository questionRepository) {
    this.jiraService = jiraService;
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getStatus() {
    return ResponseEntity.ok(
        Map.of(
            "configured",
            jiraService.isConfigured(),
            "baseUrl",
            jiraService.isConfigured() ? "configured" : "not configured"));
  }

  @PostMapping("/questionnaires/{questionnaireId}/create-issue")
  public ResponseEntity<Map<String, Object>> createIssue(@PathVariable UUID questionnaireId) {
    Questionnaire questionnaire =
        questionnaireRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(
                questionnaireId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    List<Question> questions =
        questionRepository.findByQuestionnaireIdAndDeletedAtIsNull(questionnaireId);

    Optional<String> issueKey = jiraService.createIssue(questionnaire, questions);

    if (issueKey.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "issueKey", issueKey.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to create Jira issue"));
  }

  @PostMapping("/issues/{issueKey}/subtasks")
  public ResponseEntity<Map<String, Object>> createSubTask(
      @PathVariable String issueKey, @RequestBody SubTaskRequest request) {
    Optional<String> subTaskKey =
        jiraService.createSubTask(issueKey, request.question, request.answer);
    if (subTaskKey.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "subTaskKey", subTaskKey.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to create sub-task"));
  }

  @GetMapping("/issues/{issueKey}")
  public ResponseEntity<Map<String, Object>> getIssue(@PathVariable String issueKey) {
    Optional<com.fasterxml.jackson.databind.JsonNode> issue = jiraService.getIssue(issueKey);
    if (issue.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "issue", issue.get()));
    }
    return ResponseEntity.notFound().build();
  }

  @PostMapping("/issues/{issueKey}/comments")
  public ResponseEntity<Map<String, Object>> addComment(
      @PathVariable String issueKey, @RequestBody CommentRequest request) {
    boolean success = jiraService.addComment(issueKey, request.comment);
    return ResponseEntity.ok(Map.of("success", success));
  }

  @PostMapping("/issues/{issueKey}/transitions")
  public ResponseEntity<Map<String, Object>> transitionIssue(
      @PathVariable String issueKey, @RequestBody TransitionRequest request) {
    boolean success = jiraService.transitionIssue(issueKey, request.transitionName);
    return ResponseEntity.ok(Map.of("success", success));
  }

  public record SubTaskRequest(Question question, String answer) {}

  public record CommentRequest(String comment) {}

  public record TransitionRequest(String transitionName) {}
}
