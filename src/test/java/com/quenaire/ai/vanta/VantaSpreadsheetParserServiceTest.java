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

import com.quenaire.ai.vanta.VantaSpreadsheetParserService.ParsedQuestion;
import com.quenaire.ai.vanta.VantaSpreadsheetParserService.ParsedQuestionnaire;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * The parser that every uploaded questionnaire goes through.
 *
 * <p>Pure parsing: no Spring context, no database. Each case is a header layout real questionnaires
 * use. Before these tests the most common of them -- a column headed "Question" -- produced zero
 * questions, because the header was taken for the question-number column.
 */
class VantaSpreadsheetParserServiceTest {

  private final VantaSpreadsheetParserService parser = new VantaSpreadsheetParserService();

  @Test
  @DisplayName("a column headed simply 'Question' is read as the question text")
  void plainQuestionHeader() throws IOException {
    List<ParsedQuestion> questions =
        parse(
            new String[] {"#", "Question", "Answer"},
            new String[] {"1", "Do you encrypt customer data at rest?", ""},
            new String[] {"2", "Is multi-factor authentication enforced for staff?", ""});

    assertThat(questions)
        .extracting(ParsedQuestion::getText)
        .containsExactly(
            "Do you encrypt customer data at rest?",
            "Is multi-factor authentication enforced for staff?");
    assertThat(questions).extracting(ParsedQuestion::getQuestionNumber).containsExactly("1", "2");
  }

  @Test
  @DisplayName("an id column, a text column and an evidence column each land in their own slot")
  void vendorAssessmentLayout() throws IOException {
    List<ParsedQuestion> questions =
        parse(
            new String[] {
              "Question ID", "Category", "Question Text", "Response", "Evidence", "Question Type"
            },
            new String[] {
              "AC-01",
              "Access Control",
              "Describe how access to production is reviewed.",
              "",
              "Access review policy v3",
              "TEXT"
            });

    ParsedQuestion question = questions.get(0);
    assertThat(question.getQuestionNumber()).isEqualTo("AC-01");
    assertThat(question.getCategory()).isEqualTo("Access Control");
    assertThat(question.getText()).isEqualTo("Describe how access to production is reviewed.");
    // "Evidence" contains the letters "id"; it is still not an identifier column.
    assertThat(question.getNotes()).isEqualTo("Access review policy v3");
  }

  @Test
  @DisplayName("a control framework layout reads its description as the question")
  void controlFrameworkLayout() throws IOException {
    List<ParsedQuestion> questions =
        parse(
            new String[] {"Control ID", "Control", "Control Description", "Guidance"},
            new String[] {
              "CC6.1",
              "Logical Access",
              "The entity restricts logical access to information assets.",
              "Provide your access control policy."
            });

    ParsedQuestion question = questions.get(0);
    assertThat(question.getQuestionNumber()).isEqualTo("CC6.1");
    assertThat(question.getCategory()).isEqualTo("Logical Access");
    assertThat(question.getText())
        .isEqualTo("The entity restricts logical access to information assets.");
    assertThat(question.getNotes()).isEqualTo("Provide your access control policy.");
  }

  @Test
  @DisplayName("when no header names the question column, the column of prose is used")
  void unlabelledQuestionColumn() throws IOException {
    List<ParsedQuestion> questions =
        parse(
            new String[] {"Ref", "Item"},
            new String[] {"A.1", "Do you maintain an inventory of information assets?"},
            new String[] {"A.2", "Are vendors assessed before they are given access to data?"});

    assertThat(questions)
        .extracting(ParsedQuestion::getText)
        .containsExactly(
            "Do you maintain an inventory of information assets?",
            "Are vendors assessed before they are given access to data?");
  }

  private List<ParsedQuestion> parse(String[]... rows) throws IOException {
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

    ParsedQuestionnaire parsed =
        parser.parse(
            new MockMultipartFile(
                "file",
                "questionnaire.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes.toByteArray()));

    return parsed.getSheets().stream().flatMap(sheet -> sheet.getQuestions().stream()).toList();
  }
}
