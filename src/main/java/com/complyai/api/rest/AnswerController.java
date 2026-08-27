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
package com.complyai.api.rest;

import com.complyai.ai.generation.AnswerGenerationService;
import com.complyai.ai.generation.AnswerService;
import com.complyai.domain.answer.Answer;
import com.complyai.domain.answer.AnswerRepository;
import com.complyai.domain.answer.AnswerStatus;
import com.complyai.domain.questionnaire.QuestionRepository;
import com.complyai.security.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

  private final AnswerService answerService;
  private final AnswerRepository answerRepository;
  private final QuestionRepository questionRepository;
  private final AnswerGenerationService generationService;

  public AnswerController(
      AnswerService answerService,
      AnswerRepository answerRepository,
      QuestionRepository questionRepository,
      AnswerGenerationService generationService) {
    this.answerService = answerService;
    this.answerRepository = answerRepository;
    this.questionRepository = questionRepository;
    this.generationService = generationService;
  }

  @PostMapping("/generate/{questionId}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
  public ResponseEntity<Answer> generateAnswer(@PathVariable UUID questionId) {
    Answer answer = answerService.generateAnswer(questionId);
    return ResponseEntity.ok(answer);
  }

  @PostMapping("/generate/questionnaire/{questionnaireId}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
  public ResponseEntity<Map<String, Object>> generateAnswersForQuestionnaire(
      @PathVariable UUID questionnaireId) {
    answerService.generateAnswersForQuestionnaire(questionnaireId);
    return ResponseEntity.ok(Map.of("status", "generation_started"));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Page<Answer>> list(
      @RequestParam(required = false) AnswerStatus status, Pageable pageable) {

    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    Page<Answer> answers = answerRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    return ResponseEntity.ok(answers);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Answer> get(@PathVariable UUID id) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return answerRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/question/{questionId}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Answer> getByQuestion(@PathVariable UUID questionId) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return answerRepository
        .findByQuestionId(questionId)
        .filter(a -> a.getTenantId().equals(tenantId))
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
  public ResponseEntity<Answer> update(
      @PathVariable UUID id, @RequestBody UpdateAnswerRequest request) {

    Answer answer =
        answerService.updateAnswer(id, request.content(), request.status(), request.reviewNotes());
    return ResponseEntity.ok(answer);
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
  public ResponseEntity<Answer> approve(@PathVariable UUID id) {
    Answer answer = answerService.approveAnswer(id);
    return ResponseEntity.ok(answer);
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
  public ResponseEntity<Answer> reject(@PathVariable UUID id, @RequestBody RejectRequest request) {
    Answer answer = answerService.rejectAnswer(id, request.reviewNotes());
    return ResponseEntity.ok(answer);
  }

  @GetMapping("/questionnaire/{questionnaireId}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<List<Answer>> getByQuestionnaire(@PathVariable UUID questionnaireId) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    var answers = answerRepository.findByQuestionnaireId(questionnaireId);
    var filtered = answers.stream().filter(a -> a.getTenantId().equals(tenantId)).toList();

    return ResponseEntity.ok(filtered);
  }

  @GetMapping("/stats/questionnaire/{questionnaireId}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Map<String, Object>> getQuestionnaireStats(
      @PathVariable UUID questionnaireId) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    var answers = answerRepository.findByQuestionnaireId(questionnaireId);
    var filtered = answers.stream().filter(a -> a.getTenantId().equals(tenantId)).toList();

    long total = filtered.size();
    long aiGenerated =
        filtered.stream().filter(a -> a.getStatus() == AnswerStatus.AI_GENERATED).count();
    long humanEdited =
        filtered.stream().filter(a -> a.getStatus() == AnswerStatus.HUMAN_EDITED).count();
    long approved = filtered.stream().filter(a -> a.getStatus() == AnswerStatus.APPROVED).count();
    long rejected = filtered.stream().filter(a -> a.getStatus() == AnswerStatus.REJECTED).count();
    long draft = filtered.stream().filter(a -> a.getStatus() == AnswerStatus.DRAFT).count();

    double avgConfidence =
        filtered.stream()
            .mapToDouble(a -> a.getConfidenceScore() != null ? a.getConfidenceScore() : 0.0)
            .average()
            .orElse(0.0);

    return ResponseEntity.ok(
        Map.of(
            "total", total,
            "aiGenerated", aiGenerated,
            "humanEdited", humanEdited,
            "approved", approved,
            "rejected", rejected,
            "draft", draft,
            "avgConfidence", avgConfidence,
            "completionRate", total > 0 ? (double) (approved + rejected) / total : 0.0));
  }

  public record UpdateAnswerRequest(String content, AnswerStatus status, String reviewNotes) {}

  public record RejectRequest(String reviewNotes) {}
}
