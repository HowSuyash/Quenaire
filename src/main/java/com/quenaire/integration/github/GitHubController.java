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
package com.quenaire.integration.github;

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
@RequestMapping("/api/integrations/github")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
public class GitHubController {

  private final GitHubIntegrationService githubService;
  private final QuestionnaireRepository questionnaireRepository;
  private final QuestionRepository questionRepository;

  public GitHubController(
      GitHubIntegrationService githubService,
      QuestionnaireRepository questionnaireRepository,
      QuestionRepository questionRepository) {
    this.githubService = githubService;
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getStatus() {
    return ResponseEntity.ok(
        Map.of(
            "configured",
            githubService.isConfigured(),
            "repository",
            githubService.isConfigured() ? "configured" : "not configured"));
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

    Optional<String> issueNumber = githubService.createIssue(questionnaire, questions);

    if (issueNumber.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "issueNumber", issueNumber.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to create GitHub issue"));
  }

  @PostMapping("/issues/{issueNumber}/sub-issues")
  public ResponseEntity<Map<String, Object>> createSubIssue(
      @PathVariable int issueNumber, @RequestBody SubIssueRequest request) {
    Optional<String> subIssueNumber =
        githubService.createSubIssue(issueNumber, request.question(), request.answer());
    if (subIssueNumber.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "issueNumber", subIssueNumber.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to create sub-issue"));
  }

  @GetMapping("/issues/{issueNumber}")
  public ResponseEntity<Map<String, Object>> getIssue(@PathVariable int issueNumber) {
    Optional<tools.jackson.databind.JsonNode> issue = githubService.getIssue(issueNumber);
    if (issue.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "issue", issue.get()));
    }
    return ResponseEntity.notFound().build();
  }

  @PostMapping("/issues/{issueNumber}/comments")
  public ResponseEntity<Map<String, Object>> addComment(
      @PathVariable int issueNumber, @RequestBody CommentRequest request) {
    boolean success = githubService.addComment(issueNumber, request.comment());
    return ResponseEntity.ok(Map.of("success", success));
  }

  @PostMapping("/issues/{issueNumber}/close")
  public ResponseEntity<Map<String, Object>> closeIssue(@PathVariable int issueNumber) {
    boolean success = githubService.closeIssue(issueNumber);
    return ResponseEntity.ok(Map.of("success", success));
  }

  @PostMapping("/dispatch")
  public ResponseEntity<Map<String, Object>> createDispatch(@RequestBody DispatchRequest request) {
    Optional<String> result =
        githubService.createRepositoryDispatch(request.eventType(), request.payload());
    if (result.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "result", result.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to create dispatch"));
  }

  public record SubIssueRequest(Question question, String answer) {}

  public record CommentRequest(String comment) {}

  public record DispatchRequest(String eventType, Map<String, Object> payload) {}
}
