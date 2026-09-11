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
package com.quenaire.api.rest;

import com.quenaire.ai.ingestion.QuestionnaireIngestionService;
import com.quenaire.domain.questionnaire.Question;
import com.quenaire.domain.questionnaire.QuestionRepository;
import com.quenaire.domain.questionnaire.QuestionStatus;
import com.quenaire.domain.questionnaire.Questionnaire;
import com.quenaire.domain.questionnaire.QuestionnaireRepository;
import com.quenaire.domain.questionnaire.QuestionnaireSourceType;
import com.quenaire.domain.questionnaire.QuestionnaireStatus;
import com.quenaire.security.TenantContext;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/questionnaires")
public class QuestionnaireController {

  private final QuestionnaireIngestionService ingestionService;
  private final QuestionnaireRepository questionnaireRepository;
  private final QuestionRepository questionRepository;

  public QuestionnaireController(
      QuestionnaireIngestionService ingestionService,
      QuestionnaireRepository questionnaireRepository,
      QuestionRepository questionRepository) {
    this.ingestionService = ingestionService;
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
  }

  @PostMapping(consumes = "multipart/form-data")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
  public ResponseEntity<Questionnaire> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "sourceType", defaultValue = "UPLOAD_EXCEL")
          QuestionnaireSourceType sourceType)
      throws IOException {

    Questionnaire questionnaire = ingestionService.ingest(file, sourceType);
    return ResponseEntity.ok(questionnaire);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Page<Questionnaire>> list(
      @RequestParam(required = false) QuestionnaireStatus status, Pageable pageable) {

    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    Page<Questionnaire> questionnaires =
        (status != null)
            ? questionnaireRepository.findByTenantIdAndStatusAndDeletedAtIsNull(
                tenantId, status, pageable)
            : questionnaireRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);

    return ResponseEntity.ok(questionnaires);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Questionnaire> get(@PathVariable UUID id) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return questionnaireRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}/questions")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Page<Question>> getQuestions(
      @PathVariable UUID id,
      @RequestParam(required = false) QuestionStatus status,
      Pageable pageable) {

    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    var questionnaireOpt =
        questionnaireRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId);
    if (questionnaireOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Page<Question> questions =
        (status != null)
            ? questionRepository.findByQuestionnaireIdAndStatusAndDeletedAtIsNull(
                id, status, pageable)
            : questionRepository.findByQuestionnaireIdAndDeletedAtIsNull(id, pageable);

    return ResponseEntity.ok(questions);
  }

  @GetMapping("/{id}/questions/{questionId}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Question> getQuestion(
      @PathVariable UUID id, @PathVariable UUID questionId) {

    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return questionRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(questionId, tenantId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PatchMapping("/{id}/questions/{questionId}/status")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
  public ResponseEntity<Question> updateQuestionStatus(
      @PathVariable UUID id,
      @PathVariable UUID questionId,
      @RequestBody StatusUpdateRequest request) {

    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return questionRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(questionId, tenantId)
        .map(
            question -> {
              question.setStatus(request.status());
              question = questionRepository.save(question);
              return ResponseEntity.ok(question);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return questionnaireRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
        .map(
            q -> {
              q.setDeletedAt(java.time.Instant.now());
              questionnaireRepository.save(q);
              return ResponseEntity.noContent().<Void>build();
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/stats")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Map<String, Object>> stats() {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    long total = questionnaireRepository.countByTenantIdAndDeletedAtIsNull(tenantId);
    long draft =
        questionnaireRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, QuestionnaireStatus.DRAFT);
    long processing =
        questionnaireRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, QuestionnaireStatus.PROCESSING);
    long readyForReview =
        questionnaireRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, QuestionnaireStatus.READY_FOR_REVIEW);
    long inReview =
        questionnaireRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, QuestionnaireStatus.IN_REVIEW);
    long approved =
        questionnaireRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, QuestionnaireStatus.APPROVED);
    long submitted =
        questionnaireRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, QuestionnaireStatus.SUBMITTED);

    return ResponseEntity.ok(
        Map.of(
            "total", total,
            "draft", draft,
            "processing", processing,
            "readyForReview", readyForReview,
            "inReview", inReview,
            "approved", approved,
            "submitted", submitted));
  }

  public record StatusUpdateRequest(QuestionStatus status) {}
}
