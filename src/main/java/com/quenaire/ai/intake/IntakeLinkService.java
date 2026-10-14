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
package com.quenaire.ai.intake;

import com.quenaire.ai.vanta.VantaQuestionnaireService;
import com.quenaire.domain.intake.IntakeLink;
import com.quenaire.domain.intake.IntakeLinkRepository;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.tenant.TenantRepository;
import com.quenaire.security.TenantContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Single-use links through which someone outside the customer's company sends them a
 * questionnaire.
 *
 * <p>The customer creates a link and sends it on; the recipient opens it, uploads their
 * spreadsheet, and it is parsed straight into the customer's workspace. The recipient has no
 * account, so the token in the link is the only credential: it is generated from {@link
 * SecureRandom}, only its SHA-256 is stored, and it stops working once used, revoked or expired.
 */
@Service
public class IntakeLinkService {

  private static final Logger log = LoggerFactory.getLogger(IntakeLinkService.class);

  static final int DEFAULT_EXPIRY_DAYS = 30;
  static final int MAX_EXPIRY_DAYS = 90;

  /** 256 bits, base64url without padding: 43 characters. */
  private static final int TOKEN_BYTES = 32;

  private static final int MAX_TOKEN_LENGTH = 64;

  private final IntakeLinkRepository linkRepository;
  private final TenantRepository tenantRepository;
  private final VantaQuestionnaireService questionnaireService;
  private final String frontendUrl;
  private final SecureRandom random = new SecureRandom();

  public IntakeLinkService(
      IntakeLinkRepository linkRepository,
      TenantRepository tenantRepository,
      VantaQuestionnaireService questionnaireService,
      @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
    this.linkRepository = linkRepository;
    this.tenantRepository = tenantRepository;
    this.questionnaireService = questionnaireService;
    this.frontendUrl = frontendUrl.replaceAll("/+$", "");
  }

  /** A newly created link. The URL contains the token and cannot be shown again. */
  public record CreatedLink(IntakeLink link, String url) {}

  /** What the person holding a link is allowed to see about it. */
  public record PublicStatus(
      IntakeLink.State state, String recipientName, String vendorName, Instant expiresAt) {}

  /** Why a submission was refused. Each carries the message shown to the person uploading. */
  public enum Refusal {
    NOT_FOUND("This intake link is not valid. Please ask the sender for a new one."),
    EXPIRED("This intake link has expired. Please ask the sender for a new one."),
    ALREADY_USED("A questionnaire has already been submitted through this link."),
    NO_FILE("Please choose a file to upload."),
    UNSUPPORTED_FILE(
        "Only Excel workbooks (.xlsx) can be processed. Please save the questionnaire as .xlsx"
            + " and try again."),
    UNREADABLE_FILE("We could not read this workbook. Please check the file and try again."),
    NO_QUESTIONS(
        "We could not find any questions in this workbook. Please check it is the questionnaire"
            + " and try again.");

    private final String message;

    Refusal(String message) {
      this.message = message;
    }

    public String message() {
      return message;
    }
  }

  public static class IntakeRefusedException extends RuntimeException {
    private final Refusal refusal;

    IntakeRefusedException(Refusal refusal) {
      super(refusal.message());
      this.refusal = refusal;
    }

    public Refusal refusal() {
      return refusal;
    }
  }

  // ---- Customer side ------------------------------------------------------

  @Transactional
  public CreatedLink create(String vendorName, String vendorEmail, Integer expiresInDays) {
    String name = trimmed(vendorName, 255);
    String email = trimmed(vendorEmail, 320);
    if (name == null || email == null) {
      throw new IllegalArgumentException("vendorName and vendorEmail are required");
    }
    if (!email.contains("@")) {
      throw new IllegalArgumentException("vendorEmail is not an email address");
    }
    int days = expiresInDays == null ? DEFAULT_EXPIRY_DAYS : expiresInDays;
    if (days < 1 || days > MAX_EXPIRY_DAYS) {
      throw new IllegalArgumentException(
          "expiresInDays must be between 1 and " + MAX_EXPIRY_DAYS);
    }

    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    IntakeLink link = new IntakeLink();
    link.setTenantId(TenantContext.requireCurrentTenant().getId());
    link.setTokenHash(hash(token));
    link.setVendorName(name);
    link.setVendorEmail(email.toLowerCase(Locale.ROOT));
    link.setExpiresAt(Instant.now().plus(Duration.ofDays(days)));
    link = linkRepository.save(link);

    log.info("Created intake link {} for tenant {}", link.getId(), link.getTenantId());
    return new CreatedLink(link, frontendUrl + "/intake/" + token);
  }

  @Transactional(readOnly = true)
  public List<IntakeLink> list() {
    return linkRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        TenantContext.requireCurrentTenant().getId());
  }

  /** Stops a link working. Another tenant's link is reported as not found. */
  @Transactional
  public boolean revoke(UUID linkId) {
    return linkRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(linkId, TenantContext.requireCurrentTenant().getId())
        .map(
            link -> {
              link.setDeletedAt(Instant.now());
              linkRepository.save(link);
              return true;
            })
        .orElse(false);
  }

  // ---- Public side --------------------------------------------------------

  @Transactional(readOnly = true)
  public Optional<PublicStatus> status(String token) {
    return lookup(token)
        .flatMap(linkRepository::findByTokenHashAndDeletedAtIsNull)
        .map(
            link ->
                new PublicStatus(
                    link.stateAt(Instant.now()),
                    tenantRepository.findById(link.getTenantId()).map(Tenant::getName).orElse(null),
                    link.getVendorName(),
                    link.getExpiresAt()));
  }

  /**
   * Files the uploaded workbook as a questionnaire in the link owner's workspace and closes the
   * link. Either all of that happens or none of it: a refused or unreadable upload leaves the link
   * usable for a corrected file.
   */
  @Transactional
  public VantaQuestionnaire submit(
      String token,
      MultipartFile file,
      String submitterName,
      String submitterEmail,
      String portalUrl) {
    IntakeLink link =
        lookup(token)
            .flatMap(linkRepository::findForSubmission)
            .orElseThrow(() -> new IntakeRefusedException(Refusal.NOT_FOUND));

    Instant now = Instant.now();
    switch (link.stateAt(now)) {
      case SUBMITTED -> throw new IntakeRefusedException(Refusal.ALREADY_USED);
      case EXPIRED -> throw new IntakeRefusedException(Refusal.EXPIRED);
      case PENDING -> {}
    }

    if (file == null || file.isEmpty()) {
      throw new IntakeRefusedException(Refusal.NO_FILE);
    }
    String fileName = file.getOriginalFilename();
    if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
      throw new IntakeRefusedException(Refusal.UNSUPPORTED_FILE);
    }

    String name = trimmed(submitterName, 255);
    String email = trimmed(submitterEmail, 320);
    String portal = trimmed(portalUrl, 1000);

    VantaQuestionnaire questionnaire;
    try {
      questionnaire =
          questionnaireService.uploadAndParse(
              file,
              link.getVendorName() + " security questionnaire",
              "Received through an intake link from "
                  + (name != null ? name : link.getVendorName())
                  + (email != null ? " <" + email + ">" : "")
                  + (portal != null ? ". Portal: " + portal : ""),
              link.getTenantId(),
              VantaQuestionnaire.SourceType.INTAKE_LINK);
    } catch (IOException | RuntimeException e) {
      // POI reports a corrupt or mislabelled workbook with a mix of IOExceptions and runtime
      // exceptions; to the person uploading, they are all the same problem.
      log.info("Intake link {} received a workbook that could not be read", link.getId(), e);
      throw new IntakeRefusedException(Refusal.UNREADABLE_FILE);
    }

    if (questionnaire.getTotalQuestions() == null || questionnaire.getTotalQuestions() == 0) {
      // Thrown inside the transaction, so the empty questionnaire is rolled back with it.
      throw new IntakeRefusedException(Refusal.NO_QUESTIONS);
    }

    link.markSubmitted(questionnaire.getId(), now, name, email, portal);
    linkRepository.save(link);

    log.info(
        "Intake link {} delivered questionnaire {} ({} questions) to tenant {}",
        link.getId(),
        questionnaire.getId(),
        questionnaire.getTotalQuestions(),
        link.getTenantId());
    return questionnaire;
  }

  /** The hash to look a token up by, or empty for anything that cannot be one of ours. */
  private static Optional<String> lookup(String token) {
    if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
      return Optional.empty();
    }
    return Optional.of(hash(token));
  }

  static String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
    }
  }

  private static String trimmed(String value, int maxLength) {
    if (value == null || value.isBlank()) return null;
    String trimmed = value.trim();
    return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
  }
}
