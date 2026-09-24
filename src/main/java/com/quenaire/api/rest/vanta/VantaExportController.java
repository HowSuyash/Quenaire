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

import com.quenaire.ai.vanta.VantaPdfExportService;
import com.quenaire.ai.vanta.VantaQuestionnaireService;
import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.integration.loopio.LoopioIntegrationService;
import com.quenaire.security.TenantContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * One-click export and portal submission.
 *
 * <p>Complements {@link VantaQuestionnaireController}'s Excel exports with a branded PDF and a
 * direct push into Loopio.
 */
@RestController
@RequestMapping("/api/vanta/questionnaires")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
public class VantaExportController {

  private static final Logger log = LoggerFactory.getLogger(VantaExportController.class);

  private final VantaQuestionnaireService questionnaireService;
  private final VantaPdfExportService pdfExportService;
  private final LoopioIntegrationService loopioService;
  private final ObjectMapper objectMapper;

  public VantaExportController(
      VantaQuestionnaireService questionnaireService,
      VantaPdfExportService pdfExportService,
      LoopioIntegrationService loopioService,
      ObjectMapper objectMapper) {
    this.questionnaireService = questionnaireService;
    this.pdfExportService = pdfExportService;
    this.loopioService = loopioService;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/{id}/export/pdf")
  public ResponseEntity<Resource> exportPdf(@PathVariable UUID id) throws IOException {
    VantaQuestionnaire questionnaire =
        questionnaireService
            .getQuestionnaire(id)
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    List<VantaQuestion> questions = questionnaireService.getQuestions(id);
    byte[] pdf = pdfExportService.exportQuestionnaire(questionnaire, questions, tenantBranding());

    return fileResponse(pdf, fileName(questionnaire.getTitle(), "pdf"), MediaType.APPLICATION_PDF);
  }

  /** Approved answers only — what you would actually send to a prospect. */
  @GetMapping("/{id}/export/pdf/approved")
  public ResponseEntity<Resource> exportApprovedPdf(@PathVariable UUID id) throws IOException {
    VantaQuestionnaire questionnaire =
        questionnaireService
            .getQuestionnaire(id)
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    List<VantaQuestion> approved =
        questionnaireService.getQuestions(id).stream()
            .filter(q -> q.getStatus() == VantaQuestion.QuestionStatus.APPROVED)
            .toList();

    byte[] pdf = pdfExportService.exportQuestionnaire(questionnaire, approved, tenantBranding());
    return fileResponse(
        pdf, fileName(questionnaire.getTitle() + " (approved)", "pdf"), MediaType.APPLICATION_PDF);
  }

  // ---- Portal submission --------------------------------------------------

  public record LoopioSubmitRequest(String projectId, boolean approvedOnly) {}

  @PostMapping("/{id}/submit/loopio")
  public ResponseEntity<Map<String, Object>> submitToLoopio(
      @PathVariable UUID id, @RequestBody(required = false) LoopioSubmitRequest request) {
    VantaQuestionnaire questionnaire =
        questionnaireService
            .getQuestionnaire(id)
            .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found"));

    boolean approvedOnly = request == null || request.approvedOnly();
    List<VantaQuestion> questions =
        questionnaireService.getQuestions(id).stream()
            .filter(q -> !approvedOnly || q.getStatus() == VantaQuestion.QuestionStatus.APPROVED)
            .toList();

    if (questions.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "success",
                  false,
                  "error",
                  approvedOnly
                      ? "No approved answers to submit — approve answers first."
                      : "This questionnaire has no answers yet."));
    }

    LoopioIntegrationService.SubmitResult result =
        loopioService.submitQuestionnaire(
            questionnaire, questions, request != null ? request.projectId() : null);

    return ResponseEntity.ok(
        Map.of(
            "success", result.success(),
            "projectId", result.projectId() != null ? result.projectId() : "",
            "submitted", result.submitted(),
            "failed", result.failed(),
            "errors", result.errors()));
  }

  @GetMapping("/integrations/loopio/status")
  public ResponseEntity<Map<String, Object>> loopioStatus() {
    return ResponseEntity.ok(loopioService.getStatus().orElse(Map.of("connected", false)));
  }

  @PostMapping("/integrations/loopio/projects/{projectId}/import")
  public ResponseEntity<Map<String, Object>> importLoopioProject(@PathVariable String projectId) {
    List<Map<String, Object>> questions = loopioService.importProjectQuestions(projectId);
    return ResponseEntity.ok(
        Map.of(
            "projectId", projectId,
            "questionCount", questions.size(),
            "questions", questions));
  }

  // ---- Helpers -------------------------------------------------------------

  /** Pull PDF branding from the tenant's stored branding JSON, if present. */
  private VantaPdfExportService.Branding tenantBranding() {
    Tenant tenant = TenantContext.getCurrentTenant();
    if (tenant == null) {
      return VantaPdfExportService.Branding.defaults();
    }

    String companyName = tenant.getName();
    String accent = null;
    String footer = null;

    try {
      String json = tenant.getBrandingJson();
      if (json != null && !json.isBlank()) {
        JsonNode node = objectMapper.readTree(json);
        accent = node.path("primaryColor").asText(null);
        footer = node.path("documentFooter").asText(null);
        String branded = node.path("companyName").asText(null);
        if (branded != null && !branded.isBlank()) companyName = branded;
      }
    } catch (Exception e) {
      // Branding is cosmetic — never fail an export over it.
      log.debug("Could not parse tenant branding; using defaults", e);
    }

    return new VantaPdfExportService.Branding(
        companyName, accent != null && !accent.isBlank() ? accent : "#2563EB", footer);
  }

  private static ResponseEntity<Resource> fileResponse(
      byte[] bytes, String fileName, MediaType type) {
    ByteArrayResource resource = new ByteArrayResource(bytes);
    String encoded =
        java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\""
                + fileName.replaceAll("[^\\w .()-]", "_")
                + "\"; filename*=UTF-8''"
                + encoded)
        .contentType(type)
        .contentLength(bytes.length)
        .body(resource);
  }

  private static String fileName(String title, String extension) {
    String base = (title == null || title.isBlank()) ? "questionnaire" : title;
    return base.replaceAll("[\\\\/:*?\"<>|]", "-").trim() + "." + extension;
  }
}
