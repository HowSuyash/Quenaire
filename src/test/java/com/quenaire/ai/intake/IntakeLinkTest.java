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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quenaire.IntegrationTestBase;
import com.quenaire.ai.intake.IntakeLinkService.CreatedLink;
import com.quenaire.ai.intake.IntakeLinkService.IntakeRefusedException;
import com.quenaire.ai.intake.IntakeLinkService.Refusal;
import com.quenaire.ai.vanta.VantaQuestionnaireService;
import com.quenaire.domain.intake.IntakeLink;
import com.quenaire.domain.intake.IntakeLinkRepository;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.tenant.TenantPlan;
import com.quenaire.domain.tenant.TenantRepository;
import com.quenaire.domain.tenant.TenantStatus;
import com.quenaire.security.TenantContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/**
 * A questionnaire sent through an intake link reaches the customer who sent the link, once.
 *
 * <p>The previous implementation stored no links, accepted any token, and threw the upload away
 * while telling the sender it was being processed. These tests are written from the two sides of
 * the link: the customer who creates it, and the stranger who only has the URL.
 */
class IntakeLinkTest extends IntegrationTestBase {

  @Autowired IntakeLinkService intakeLinkService;
  @Autowired IntakeLinkRepository linkRepository;
  @Autowired VantaQuestionnaireService questionnaireService;
  @Autowired TenantRepository tenantRepository;

  private Tenant customer;
  private Tenant otherCustomer;

  @BeforeEach
  void setUp() {
    customer = newTenant("Northwind");
    otherCustomer = newTenant("Contoso");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("an upload through the link becomes a questionnaire in the link owner's workspace")
  void submissionReachesTheOwner() throws IOException {
    String token = tokenOf(createLinkAs(customer));

    // The stranger: no tenant, no user.
    TenantContext.clear();
    VantaQuestionnaire received =
        intakeLinkService.submit(
            token, questionnaire(), "Dana Reyes", "dana@acme.example", null);

    assertThat(received.getTotalQuestions()).isEqualTo(2);

    TenantContext.setCurrentTenant(customer);
    assertThat(questionnaireService.getQuestionnaire(received.getId()))
        .map(VantaQuestionnaire::getSourceType)
        .contains(VantaQuestionnaire.SourceType.INTAKE_LINK);
    assertThat(intakeLinkService.list())
        .singleElement()
        .satisfies(
            link -> {
              assertThat(link.stateAt(Instant.now())).isEqualTo(IntakeLink.State.SUBMITTED);
              assertThat(link.getQuestionnaireId()).isEqualTo(received.getId());
              assertThat(link.getSubmitterEmail()).isEqualTo("dana@acme.example");
            });
  }

  @Test
  @DisplayName("a link works once")
  void singleUse() throws IOException {
    String token = tokenOf(createLinkAs(customer));
    submit(token, questionnaire());

    assertThat(refusalOf(() -> submit(token, questionnaire())))
        .isEqualTo(Refusal.ALREADY_USED);
    assertThat(intakeLinkService.status(token))
        .map(IntakeLinkService.PublicStatus::state)
        .contains(IntakeLink.State.SUBMITTED);
  }

  @Test
  @DisplayName("an invented token is refused, where any token used to be accepted")
  void unknownToken() {
    assertThat(intakeLinkService.status("not-a-real-token")).isEmpty();
    assertThat(refusalOf(() -> submit("not-a-real-token", questionnaire())))
        .isEqualTo(Refusal.NOT_FOUND);
  }

  @Test
  @DisplayName("an expired link is refused")
  void expiredLink() {
    CreatedLink created = createLinkAs(customer);
    IntakeLink link = created.link();
    link.setExpiresAt(Instant.now().minusSeconds(60));
    linkRepository.save(link);

    assertThat(refusalOf(() -> submit(tokenOf(created), questionnaire())))
        .isEqualTo(Refusal.EXPIRED);
  }

  @Test
  @DisplayName("a file that is not a workbook is refused and the link stays usable")
  void wrongFileKeepsTheLinkOpen() throws IOException {
    String token = tokenOf(createLinkAs(customer));
    MockMultipartFile pdf =
        new MockMultipartFile("file", "questionnaire.pdf", "application/pdf", new byte[] {1, 2, 3});
    MockMultipartFile mislabelled =
        new MockMultipartFile("file", "questionnaire.xlsx", "text/plain", new byte[1]);

    assertThat(refusalOf(() -> submit(token, pdf))).isEqualTo(Refusal.UNSUPPORTED_FILE);
    assertThat(refusalOf(() -> submit(token, mislabelled))).isEqualTo(Refusal.UNREADABLE_FILE);

    // The corrected file still goes through.
    assertThat(submit(token, questionnaire()).getTotalQuestions()).isEqualTo(2);
  }

  private VantaQuestionnaire submit(String token, MockMultipartFile file) {
    return intakeLinkService.submit(token, file, null, null, null);
  }

  @Test
  @DisplayName("the token itself is not stored, only its hash")
  void tokenIsHashed() {
    CreatedLink created = createLinkAs(customer);
    String token = tokenOf(created);

    IntakeLink stored = linkRepository.findById(created.link().getId()).orElseThrow();
    assertThat(stored.getTokenHash()).isNotEqualTo(token).hasSize(64);
    assertThat(token).hasSizeGreaterThanOrEqualTo(43);
  }

  @Test
  @DisplayName("one customer cannot see or revoke another customer's links")
  void tenantIsolation() {
    CreatedLink created = createLinkAs(customer);

    TenantContext.setCurrentTenant(otherCustomer);
    assertThat(intakeLinkService.list()).isEmpty();
    assertThat(intakeLinkService.revoke(created.link().getId())).isFalse();

    TenantContext.setCurrentTenant(customer);
    assertThat(intakeLinkService.revoke(created.link().getId())).isTrue();
    assertThat(intakeLinkService.status(tokenOf(created))).isEmpty();
  }

  private CreatedLink createLinkAs(Tenant tenant) {
    TenantContext.setCurrentTenant(tenant);
    return intakeLinkService.create("Acme Corp", "security@acme.example", 30);
  }

  private static String tokenOf(CreatedLink created) {
    return created.url().substring(created.url().lastIndexOf('/') + 1);
  }

  private static Refusal refusalOf(Runnable submission) {
    try {
      submission.run();
    } catch (IntakeRefusedException e) {
      return e.refusal();
    }
    throw new AssertionError("Expected the submission to be refused");
  }

  private Tenant newTenant(String name) {
    Tenant tenant = new Tenant();
    tenant.setName(name);
    tenant.setSlug(name.toLowerCase() + "-" + System.nanoTime());
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setPlan(TenantPlan.FREE);
    return tenantRepository.save(tenant);
  }

  private static MockMultipartFile questionnaire() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Questionnaire");
      String[][] rows = {
        {"#", "Question"},
        {"1", "Do you encrypt customer data at rest?"},
        {"2", "Do you run annual penetration tests?"}
      };
      for (int r = 0; r < rows.length; r++) {
        Row row = sheet.createRow(r);
        for (int c = 0; c < rows[r].length; c++) {
          row.createCell(c).setCellValue(rows[r][c]);
        }
      }
      workbook.write(bytes);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return new MockMultipartFile(
        "file",
        "acme-assessment.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        bytes.toByteArray());
  }
}
