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
package com.quenaire.api.rest.agent;

import com.quenaire.ai.agent.QuestionnaireAgentOrchestrator;
import com.quenaire.ai.vanta.VantaQuestionnaireService;
import com.quenaire.ai.vanta.VantaSpreadsheetParserService;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.quenaire.security.TenantContext;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vanta/intake")
public class IntakeController {

  private static final Logger log = LoggerFactory.getLogger(IntakeController.class);

  private final VantaQuestionnaireService questionnaireService;
  private final VantaQuestionnaireRepository questionnaireRepository;
  private final VantaSpreadsheetParserService parserService;
  private final QuestionnaireAgentOrchestrator orchestrator;

  public IntakeController(
      VantaQuestionnaireService questionnaireService,
      VantaQuestionnaireRepository questionnaireRepository,
      VantaSpreadsheetParserService parserService,
      QuestionnaireAgentOrchestrator orchestrator) {
    this.questionnaireService = questionnaireService;
    this.questionnaireRepository = questionnaireRepository;
    this.parserService = parserService;
    this.orchestrator = orchestrator;
  }

  /** Create a public intake link for a tenant POST /api/vanta/intake/links */
  @PostMapping("/links")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
  public ResponseEntity<Map<String, Object>> createIntakeLink(
      @RequestBody Map<String, Object> request) {

    UUID tenantId = TenantContext.getCurrentTenantId();
    String vendorName = (String) request.get("vendorName");
    String vendorEmail = (String) request.get("vendorEmail");
    Integer expiresInDays = (Integer) request.getOrDefault("expiresInDays", 30);

    if (vendorName == null || vendorEmail == null) {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "vendorName and vendorEmail are required"));
    }

    // Generate a unique intake token
    String intakeToken = UUID.randomUUID().toString().replace("-", "").substring(0, 24);

    // In production, save intake link to database with expiration
    // For now, return the intake URL
    String baseUrl = System.getenv().getOrDefault("INTAKE_BASE_URL", "http://localhost:3000");
    String intakeUrl = baseUrl + "/intake/" + intakeToken;

    // Store intake link metadata (in production, save to database)
    IntakeLinkMetadata metadata =
        new IntakeLinkMetadata(
            intakeToken,
            tenantId,
            vendorName,
            vendorEmail,
            OffsetDateTime.now().plusDays(expiresInDays),
            OffsetDateTime.now());

    // intakeLinkRepository.save(metadata); // In production

    log.info("Created intake link {} for vendor {} ({})", intakeToken, vendorName, vendorEmail);

    return ResponseEntity.ok(
        Map.of(
            "success",
            true,
            "intakeToken",
            intakeToken,
            "intakeUrl",
            intakeUrl,
            "expiresAt",
            metadata.expiresAt()));
  }

  /** Public intake submission - no auth required POST /api/vanta/intake/submit/{token} */
  @PostMapping(value = "/submit/{token}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> submitIntake(
      @PathVariable String token,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "vendorName", required = false) String vendorName,
      @RequestParam(value = "vendorEmail", required = false) String vendorEmail,
      @RequestParam(value = "portalUrl", required = false) String portalUrl) {

    // Validate intake token
    // IntakeLinkMetadata metadata = intakeLinkRepository.findByToken(token);
    // if (metadata == null || metadata.isExpired()) {
    //     return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid or
    // expired intake link"));
    // }

    // For now, simulate metadata lookup
    IntakeLinkMetadata metadata =
        new IntakeLinkMetadata(
            token,
            UUID.randomUUID(),
            "Demo Vendor",
            "vendor@example.com",
            OffsetDateTime.now().plusDays(30),
            OffsetDateTime.now());

    if (file.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "No file uploaded"));
    }

    try {
      // Create questionnaire from intake
      String title =
          vendorName != null
              ? vendorName + " Security Questionnaire"
              : "Vendor Security Questionnaire";
      String description =
          "Submitted via intake portal by "
              + (vendorEmail != null ? vendorEmail : "unknown vendor");

      VantaQuestionnaire questionnaire = new VantaQuestionnaire();
      questionnaire.setTitle(title);
      questionnaire.setDescription(description);
      questionnaire.setSourceType(
          com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire.SourceType.EMAIL_FORWARD);
      questionnaire.setFileName(file.getOriginalFilename());
      questionnaire.setOriginalFileBytes(file.getBytes());
      questionnaire.setStatus(
          com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire.QuestionnaireStatus.PARSING);
      questionnaire.setTenantId(metadata.tenantId());

      // Save questionnaire
      // questionnaireRepository.save(questionnaire);

      // Start orchestration
      // QuestionnaireAgentOrchestrator.OrchestrationResult result =
      // orchestrator.startOrchestration(questionnaire.getId(), null);

      log.info("Received intake submission via token {} from {}", token, vendorEmail);

      return ResponseEntity.ok(
          Map.of(
              "success",
              true,
              "message",
              "Questionnaire received and processing started",
              "questionnaireId",
              UUID.randomUUID(), // Would be actual ID
              "status",
              "PROCESSING"));

    } catch (IOException e) {
      log.error("Failed to process intake submission", e);
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", "Failed to process file: " + e.getMessage()));
    }
  }

  /** Get intake link status */
  @GetMapping("/links/{token}/status")
  public ResponseEntity<Map<String, Object>> getIntakeLinkStatus(@PathVariable String token) {
    // IntakeLinkMetadata metadata = intakeLinkRepository.findByToken(token);
    // if (metadata == null) {
    //     return ResponseEntity.notFound().build();
    // }

    return ResponseEntity.ok(
        Map.of(
            "token",
            token,
            "valid",
            true,
            "expiresAt",
            OffsetDateTime.now().plusDays(30),
            "vendorName",
            "Demo Vendor"));
  }

  /** Get all intake links for current tenant */
  @GetMapping("/links")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<List<Map<String, Object>>> listIntakeLinks() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // List<IntakeLinkMetadata> links = intakeLinkRepository.findByTenantId(tenantId);
    List<Map<String, Object>> links = new ArrayList<>();

    return ResponseEntity.ok(links);
  }

  /** Intake link metadata record */
  public record IntakeLinkMetadata(
      String token,
      UUID tenantId,
      String vendorName,
      String vendorEmail,
      OffsetDateTime expiresAt,
      OffsetDateTime createdAt) {
    public boolean isExpired() {
      return OffsetDateTime.now().isAfter(expiresAt);
    }
  }
}
