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
package com.quenaire.ai.vanta;

import static org.assertj.core.api.Assertions.assertThat;

import com.quenaire.IntegrationTestBase;
import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.tenant.TenantPlan;
import com.quenaire.domain.tenant.TenantRepository;
import com.quenaire.domain.tenant.TenantStatus;
import com.quenaire.security.TenantContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/**
 * A spreadsheet uploaded through the product becomes a questionnaire its tenant can open.
 *
 * <p>Every other test builds its rows with {@code setTenant(...)}. The upload path used {@code
 * setTenantId(...)}, which on these entities sets a read-only field: the row reached Postgres with
 * a null tenant_id and the insert was refused. Nothing exercised the path a customer actually takes.
 */
class QuestionnaireUploadTest extends IntegrationTestBase {

  @Autowired VantaQuestionnaireService questionnaireService;
  @Autowired TenantRepository tenantRepository;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("an uploaded spreadsheet is stored, with its questions, under the uploader's tenant")
  void uploadIsStoredForTheTenant() throws IOException {
    Tenant tenant = new Tenant();
    tenant.setName("uploader");
    tenant.setSlug("uploader-" + System.nanoTime());
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setPlan(TenantPlan.FREE);
    tenant = tenantRepository.save(tenant);
    TenantContext.setCurrentTenant(tenant);

    VantaQuestionnaire uploaded =
        questionnaireService.uploadAndParse(
            workbook(
                new String[] {"#", "Question"},
                new String[] {"1", "Do you encrypt customer data at rest?"},
                new String[] {"2", "Do you run annual penetration tests?"}),
            "Northbridge Vendor Assessment",
            null);

    assertThat(questionnaireService.getQuestionnaire(uploaded.getId()))
        .map(VantaQuestionnaire::getTotalQuestions)
        .contains(2);
    assertThat(questionnaireService.getQuestions(uploaded.getId()))
        .extracting(VantaQuestion::getTenantId)
        .containsOnly(tenant.getId());
  }

  private static MockMultipartFile workbook(String[]... rows) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Questionnaire");
      for (int r = 0; r < rows.length; r++) {
        Row row = sheet.createRow(r);
        for (int c = 0; c < rows[r].length; c++) {
          row.createCell(c).setCellValue(rows[r][c]);
        }
      }
      workbook.write(bytes);
    }
    return new MockMultipartFile(
        "file",
        "assessment.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        bytes.toByteArray());
  }
}
