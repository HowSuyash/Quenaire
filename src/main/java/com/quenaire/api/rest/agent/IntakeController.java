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

import com.quenaire.ai.intake.IntakeLinkService;
import com.quenaire.ai.intake.IntakeLinkService.IntakeRefusedException;
import com.quenaire.ai.intake.IntakeLinkService.PublicStatus;
import com.quenaire.domain.intake.IntakeLink;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Intake links: how someone outside a customer's company sends them a questionnaire.
 *
 * <p>Creating, listing and revoking links needs a signed-in member of the tenant. Checking a link
 * and submitting through it are public -- see SecurityConfig -- and the token in the path is the
 * only credential, so those two endpoints say nothing about a link beyond what its holder needs.
 *
 * <p>Every endpoint here was a placeholder: links were not stored, any token was accepted, and an
 * upload was attached to a random tenant id, not saved, and reported as "processing started".
 */
@RestController
@RequestMapping("/api/vanta/intake")
public class IntakeController {

  private final IntakeLinkService intakeLinkService;

  public IntakeController(IntakeLinkService intakeLinkService) {
    this.intakeLinkService = intakeLinkService;
  }

  public record CreateIntakeLinkRequest(
      String vendorName, String vendorEmail, Integer expiresInDays) {}

  public record IntakeLinkResponse(
      UUID id,
      String vendorName,
      String vendorEmail,
      IntakeLink.State state,
      Instant expiresAt,
      Instant createdAt,
      UUID questionnaireId,
      Instant submittedAt,
      String submitterName,
      String submitterEmail) {

    static IntakeLinkResponse of(IntakeLink link) {
      return new IntakeLinkResponse(
          link.getId(),
          link.getVendorName(),
          link.getVendorEmail(),
          link.stateAt(Instant.now()),
          link.getExpiresAt(),
          link.getCreatedAt(),
          link.getQuestionnaireId(),
          link.getSubmittedAt(),
          link.getSubmitterName(),
          link.getSubmitterEmail());
    }
  }

  /** POST /api/vanta/intake/links -- the response is the only time the full URL is available. */
  @PostMapping("/links")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
  public ResponseEntity<Map<String, Object>> createIntakeLink(
      @RequestBody CreateIntakeLinkRequest request) {
    try {
      IntakeLinkService.CreatedLink created =
          intakeLinkService.create(
              request.vendorName(), request.vendorEmail(), request.expiresInDays());
      return ResponseEntity.ok(
          Map.of(
              "success", true,
              "link", IntakeLinkResponse.of(created.link()),
              "intakeUrl", created.url()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  /**
   * GET /api/vanta/intake/links -- the tenant's links, newest first.
   *
   * <p>Open to members as well as admins: members can create links, and could not otherwise see
   * whether the ones they sent had been used.
   */
  @GetMapping("/links")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
  public ResponseEntity<List<IntakeLinkResponse>> listIntakeLinks() {
    return ResponseEntity.ok(
        intakeLinkService.list().stream().map(IntakeLinkResponse::of).toList());
  }

  /**
   * DELETE /api/vanta/intake/links/{id} -- stops a link working, e.g. one sent to the wrong person.
   */
  @DeleteMapping("/links/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
  public ResponseEntity<Void> revokeIntakeLink(@PathVariable UUID id) {
    return intakeLinkService.revoke(id)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }

  /** GET /api/vanta/intake/links/{token}/status -- public. */
  @GetMapping("/links/{token}/status")
  public ResponseEntity<Map<String, Object>> getIntakeLinkStatus(@PathVariable String token) {
    PublicStatus status = intakeLinkService.status(token).orElse(null);
    if (status == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              Map.of("valid", false, "error", IntakeLinkService.Refusal.NOT_FOUND.message()));
    }

    // HashMap: recipientName can be null, which Map.of refuses.
    Map<String, Object> body = new HashMap<>();
    body.put("recipientName", status.recipientName());
    body.put("vendorName", status.vendorName());
    body.put("expiresAt", status.expiresAt());
    switch (status.state()) {
      case PENDING -> {
        body.put("valid", true);
        body.put("status", "PENDING");
        body.put("progress", 0);
        body.put("currentStage", "Waiting for your questionnaire");
      }
      case SUBMITTED -> {
        body.put("valid", true);
        body.put("status", "COMPLETED");
        body.put("progress", 100);
        body.put("currentStage", "Questionnaire received");
      }
      case EXPIRED -> {
        body.put("valid", false);
        body.put("status", "EXPIRED");
        body.put("error", IntakeLinkService.Refusal.EXPIRED.message());
      }
    }
    return ResponseEntity.ok(body);
  }

  /** POST /api/vanta/intake/submit/{token} -- public. */
  @PostMapping(value = "/submit/{token}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> submitIntake(
      @PathVariable String token,
      @RequestParam(value = "file", required = false) MultipartFile file,
      @RequestParam(value = "vendorName", required = false) String vendorName,
      @RequestParam(value = "vendorEmail", required = false) String vendorEmail,
      @RequestParam(value = "portalUrl", required = false) String portalUrl) {
    try {
      VantaQuestionnaire questionnaire =
          intakeLinkService.submit(token, file, vendorName, vendorEmail, portalUrl);
      // No questionnaire id: the person uploading has no account, and nothing to use it for.
      return ResponseEntity.ok(
          Map.of(
              "success", true,
              "status", "COMPLETED",
              "totalQuestions", questionnaire.getTotalQuestions(),
              "message", "Questionnaire received."));
    } catch (IntakeRefusedException e) {
      return ResponseEntity.status(statusFor(e.refusal()))
          .body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  private static HttpStatus statusFor(IntakeLinkService.Refusal refusal) {
    return switch (refusal) {
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case EXPIRED -> HttpStatus.GONE;
      case ALREADY_USED -> HttpStatus.CONFLICT;
      case NO_FILE, UNSUPPORTED_FILE, UNREADABLE_FILE, NO_QUESTIONS -> HttpStatus.BAD_REQUEST;
    };
  }
}
