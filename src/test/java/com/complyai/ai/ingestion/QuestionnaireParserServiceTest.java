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
package com.complyai.ai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class QuestionnaireParserServiceTest {
  // Pure parsing: no Spring context, no database.
  private final QuestionnaireParserService parserService = new QuestionnaireParserService();

  @Test
  void parseExcelWithQuestions() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Questions");

      // Header row
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("Question");
      header.createCell(1).setCellValue("Section");
      header.createCell(2).setCellValue("Subsection");
      header.createCell(3).setCellValue("Number");
      header.createCell(4).setCellValue("Type");
      header.createCell(5).setCellValue("Options");
      header.createCell(6).setCellValue("Required");
      header.createCell(7).setCellValue("Tags");

      // Question 1
      Row row1 = sheet.createRow(1);
      row1.createCell(0).setCellValue("What is your encryption standard?");
      row1.createCell(1).setCellValue("Security");
      row1.createCell(2).setCellValue("Encryption");
      row1.createCell(3).setCellValue("1.1");
      row1.createCell(4).setCellValue("SINGLE_CHOICE");
      row1.createCell(5).setCellValue("AES-256|AES-128|RSA");
      row1.createCell(6).setCellValue("true");
      row1.createCell(7).setCellValue("encryption;compliance");

      // Question 2
      Row row2 = sheet.createRow(2);
      row2.createCell(0).setCellValue("Describe your incident response process");
      row2.createCell(1).setCellValue("Security");
      row2.createCell(2).setCellValue("Incident Response");
      row2.createCell(3).setCellValue("1.2");
      row2.createCell(4).setCellValue("FREE_TEXT");
      row2.createCell(6).setCellValue("true");

      workbook.write(baos);
    }

    MockMultipartFile file =
        new MockMultipartFile(
            "questionnaire.xlsx",
            "questionnaire.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            baos.toByteArray());

    var result = parserService.parse(file);

    assertThat(result.fileName()).isEqualTo("questionnaire.xlsx");
    assertThat(result.questions()).hasSize(2);

    var q1 = result.questions().get(0);
    assertThat(q1.text()).isEqualTo("What is your encryption standard?");
    assertThat(q1.type()).isEqualTo(com.complyai.domain.questionnaire.QuestionType.SINGLE_CHOICE);
    assertThat(q1.options()).containsExactly("AES-256", "AES-128", "RSA");
    assertThat(q1.section()).isEqualTo("Security");
    assertThat(q1.subsection()).isEqualTo("Encryption");
    assertThat(q1.required()).isTrue();
    assertThat(q1.tags()).containsExactly("encryption", "compliance");

    var q2 = result.questions().get(1);
    assertThat(q2.text()).isEqualTo("Describe your incident response process");
    assertThat(q2.type()).isEqualTo(com.complyai.domain.questionnaire.QuestionType.FREE_TEXT);
    assertThat(q2.required()).isTrue();
  }

  @Test
  void parseExcelWithYesNoQuestion() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Questions");

      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("Question");
      header.createCell(4).setCellValue("Type");

      Row row1 = sheet.createRow(1);
      row1.createCell(0).setCellValue("Do you have a WAF? Yes No");
      row1.createCell(4).setCellValue("YES_NO");

      workbook.write(baos);
    }

    MockMultipartFile file =
        new MockMultipartFile(
            "questionnaire.xlsx",
            "questionnaire.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            baos.toByteArray());

    var result = parserService.parse(file);

    assertThat(result.questions()).hasSize(1);
    assertThat(result.questions().get(0).type())
        .isEqualTo(com.complyai.domain.questionnaire.QuestionType.YES_NO);
    assertThat(result.questions().get(0).options()).containsExactly("Yes", "No");
  }
}
