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

import com.complyai.domain.questionnaire.QuestionType;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionnaireParserService {

  private static final Pattern QUESTION_NUMBER_PATTERN =
      Pattern.compile("^(\\d+(\\.\\d+)*)[\\.)\\s]+");
  private static final Pattern QUESTION_TYPE_PATTERN =
      Pattern.compile(
          "\\[(FREE_TEXT|SINGLE_CHOICE|MULTIPLE_CHOICE|YES_NO|NUMERIC|DATE|FILE_UPLOAD|TABLE)\\]",
          Pattern.CASE_INSENSITIVE);

  public ParsedQuestionnaire parse(MultipartFile file) throws IOException {
    String filename = file.getOriginalFilename();
    if (filename == null) {
      throw new IllegalArgumentException("Filename is required");
    }

    if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
      return parseExcel(file);
    } else if (filename.endsWith(".csv")) {
      return parseCsv(file);
    } else {
      throw new UnsupportedFormatException("Unsupported file format: " + filename);
    }
  }

  private ParsedQuestionnaire parseExcel(MultipartFile file) throws IOException {
    try (InputStream is = file.getInputStream();
        Workbook workbook = new XSSFWorkbook(is)) {
      Sheet sheet = workbook.getSheetAt(0);
      List<ParsedQuestion> questions = new ArrayList<>();
      String currentSection = null;
      String currentSubsection = null;

      for (Row row : sheet) {
        if (isHeaderRow(row)) {
          continue;
        }

        ParsedQuestion question = parseRow(row, currentSection, currentSubsection);
        if (question != null) {
          if (question.section() != null && !question.section().isBlank()) {
            currentSection = question.section();
          }
          if (question.subsection() != null && !question.subsection().isBlank()) {
            currentSubsection = question.subsection();
          }
          questions.add(question);
        }
      }

      return new ParsedQuestionnaire(file.getOriginalFilename(), questions);
    }
  }

  private ParsedQuestionnaire parseCsv(MultipartFile file) throws IOException {
    try (InputStream is = file.getInputStream();
        Workbook workbook = WorkbookFactory.create(is)) {
      Sheet sheet = workbook.getSheetAt(0);
      List<ParsedQuestion> questions = new ArrayList<>();
      String currentSection = null;
      String currentSubsection = null;

      for (Row row : sheet) {
        if (isHeaderRow(row)) {
          continue;
        }

        ParsedQuestion question = parseRow(row, currentSection, currentSubsection);
        if (question != null) {
          if (question.section() != null && !question.section().isBlank()) {
            currentSection = question.section();
          }
          if (question.subsection() != null && !question.subsection().isBlank()) {
            currentSubsection = question.subsection();
          }
          questions.add(question);
        }
      }

      return new ParsedQuestionnaire(file.getOriginalFilename(), questions);
    }
  }

  private boolean isHeaderRow(Row row) {
    if (row == null || row.getRowNum() == 0) {
      return true;
    }
    Cell firstCell = row.getCell(0);
    return firstCell != null
        && firstCell.getCellType() == CellType.STRING
        && firstCell.getStringCellValue().toLowerCase().contains("question");
  }

  private ParsedQuestion parseRow(Row row, String defaultSection, String defaultSubsection) {
    if (row == null) return null;

    String questionText = getCellStringValue(row, 0);
    if (questionText == null || questionText.isBlank()) {
      return null;
    }

    String section = getCellStringValue(row, 1);
    String subsection = getCellStringValue(row, 2);
    String questionNumber = getCellStringValue(row, 3);
    String typeHint = getCellStringValue(row, 4);
    String options = getCellStringValue(row, 5);
    String required = getCellStringValue(row, 6);
    String tags = getCellStringValue(row, 7);

    QuestionType questionType = detectQuestionType(questionText, typeHint, options);
    List<String> optionList = parseOptions(options, questionType);

    return new ParsedQuestion(
        questionText.trim(),
        questionType,
        optionList,
        section != null ? section : defaultSection,
        subsection != null ? subsection : defaultSubsection,
        questionNumber,
        Boolean.parseBoolean(required != null ? required : "false"),
        parseTags(tags));
  }

  private String getCellStringValue(Row row, int cellIndex) {
    Cell cell = row.getCell(cellIndex);
    if (cell == null) return null;

    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> String.valueOf(cell.getNumericCellValue());
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> cell.getStringCellValue();
      default -> null;
    };
  }

  private QuestionType detectQuestionType(String questionText, String typeHint, String options) {
    if (typeHint != null && !typeHint.isBlank()) {
      try {
        return QuestionType.valueOf(typeHint.toUpperCase());
      } catch (IllegalArgumentException ignored) {
      }
    }

    Matcher matcher = QUESTION_TYPE_PATTERN.matcher(questionText);
    if (matcher.find()) {
      try {
        return QuestionType.valueOf(matcher.group(1).toUpperCase());
      } catch (IllegalArgumentException ignored) {
      }
    }

    if (options != null && !options.isBlank()) {
      return QuestionType.MULTIPLE_CHOICE;
    }

    String lower = questionText.toLowerCase();
    if (lower.contains("yes") && lower.contains("no") && lower.length() < 100) {
      return QuestionType.YES_NO;
    }

    return QuestionType.FREE_TEXT;
  }

  private List<String> parseOptions(String options, QuestionType type) {
    if (options == null || options.isBlank()) {
      return type == QuestionType.YES_NO ? List.of("Yes", "No") : List.of();
    }

    return Arrays.stream(options.split("[|;,]"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private List<String> parseTags(String tags) {
    if (tags == null || tags.isBlank()) {
      return List.of();
    }
    return Arrays.stream(tags.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  public record ParsedQuestionnaire(String fileName, List<ParsedQuestion> questions) {}

  public record ParsedQuestion(
      String text,
      QuestionType type,
      List<String> options,
      String section,
      String subsection,
      String questionNumber,
      boolean required,
      List<String> tags) {}

  public static class UnsupportedFormatException extends RuntimeException {
    public UnsupportedFormatException(String message) {
      super(message);
    }
  }
}
