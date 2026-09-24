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
package com.quenaire.integration.confluence;

import com.quenaire.domain.document.Document;
import com.quenaire.domain.document.DocumentRepository;
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
@RequestMapping("/api/integrations/confluence")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
public class ConfluenceController {

  private final ConfluenceIntegrationService confluenceService;
  private final QuestionnaireRepository questionnaireRepository;
  private final QuestionRepository questionRepository;
  private final DocumentRepository documentRepository;

  public ConfluenceController(
      ConfluenceIntegrationService confluenceService,
      QuestionnaireRepository questionnaireRepository,
      QuestionRepository questionRepository,
      DocumentRepository documentRepository) {
    this.confluenceService = confluenceService;
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
    this.documentRepository = documentRepository;
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getStatus() {
    return ResponseEntity.ok(
        Map.of(
            "configured",
            confluenceService.isConfigured(),
            "spaceKey",
            confluenceService.isConfigured() ? "configured" : "not configured"));
  }

  @PostMapping("/questionnaires/{questionnaireId}/create-page")
  public ResponseEntity<Map<String, Object>> createQuestionnairePage(
      @PathVariable UUID questionnaireId) {
    Questionnaire questionnaire =
        questionnaireRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(
                questionnaireId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    List<Question> questions =
        questionRepository.findByQuestionnaireIdAndDeletedAtIsNull(questionnaireId);

    Optional<String> pageId = confluenceService.createQuestionnairePage(questionnaire, questions);

    if (pageId.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "pageId", pageId.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to create Confluence page"));
  }

  @PostMapping("/documents/{documentId}/create-page")
  public ResponseEntity<Map<String, Object>> createDocumentPage(@PathVariable UUID documentId) {
    Document document =
        documentRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(documentId, TenantContext.getCurrentTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));

    Optional<String> pageId = confluenceService.createDocumentPage(document);

    if (pageId.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "pageId", pageId.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to create Confluence page"));
  }

  @GetMapping("/pages/{pageId}")
  public ResponseEntity<Map<String, Object>> getPage(@PathVariable String pageId) {
    Optional<tools.jackson.databind.JsonNode> page = confluenceService.getPage(pageId);
    if (page.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "page", page.get()));
    }
    return ResponseEntity.notFound().build();
  }

  @PutMapping("/pages/{pageId}")
  public ResponseEntity<Map<String, Object>> updatePage(
      @PathVariable String pageId, @RequestBody UpdatePageRequest request) {
    Optional<String> updatedId =
        confluenceService.updatePage(pageId, request.title(), request.content(), request.version());
    if (updatedId.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "pageId", updatedId.get()));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "error", "Failed to update page"));
  }

  @GetMapping("/search")
  public ResponseEntity<Map<String, Object>> searchPages(@RequestParam String query) {
    Optional<String> results = confluenceService.searchPages(query);
    if (results.isPresent()) {
      return ResponseEntity.ok(Map.of("success", true, "results", results.get()));
    }
    return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Search failed"));
  }

  public record UpdatePageRequest(String title, String content, int version) {}
}
