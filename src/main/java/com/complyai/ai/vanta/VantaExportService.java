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
package com.complyai.ai.vanta;

import com.complyai.ai.vanta.VantaSpreadsheetParserService.ParsedSheet;
import com.complyai.domain.questionnaire.vanta.VantaQuestion;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaire;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VantaExportService {

  private static final Logger log = LoggerFactory.getLogger(VantaExportService.class);

  public byte[] exportToOriginalExcel(VantaQuestionnaire questionnaire, byte[] originalFileBytes)
      throws IOException {
    log.info("Exporting questionnaire {} to original Excel format", questionnaire.getId());

    try (InputStream is = new ByteArrayInputStream(originalFileBytes);
        Workbook workbook = new XSSFWorkbook(is);
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      // Create map of answers by coordinates
      Map<String, String> answerMap = buildAnswerMap(questionnaire);

      // Update each sheet
      for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
        Sheet sheet = workbook.getSheetAt(sheetIdx);
        String sheetName = sheet.getSheetName();

        ParsedSheet parsedSheet = parseSheetForExport(sheet, sheetIdx);
        int headerRowIdx = parsedSheet.getHeaderRowIndex();

        // Find answer column
        int answerColIdx = findAnswerColumn(sheet, headerRowIdx);
        if (answerColIdx < 0) {
          log.warn("No answer column found in sheet: {}", sheetName);
          continue;
        }

        // Update answer cells
        for (int rowIdx = headerRowIdx + 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
          Row row = sheet.getRow(rowIdx);
          if (row == null || isEmptyRow(row)) continue;

          String key = buildKey(sheetName, rowIdx);
          String answer = answerMap.get(key);

          if (answer != null) {
            Cell answerCell = row.getCell(answerColIdx);
            if (answerCell == null) {
              answerCell = row.createCell(answerColIdx);
            }
            setCellValuePreservingStyle(answerCell, answer);
          }
        }
      }

      workbook.write(baos);
      return baos.toByteArray();
    }
  }

  public byte[] exportToNewExcel(VantaQuestionnaire questionnaire) throws IOException {
    log.info("Exporting questionnaire {} to new formatted Excel", questionnaire.getId());

    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      // Group questions by sheet
      Map<String, List<VantaQuestion>> questionsBySheet =
          questionnaire.getQuestions().stream()
              .collect(
                  Collectors.groupingBy(
                      q -> q.getSheetName() != null ? q.getSheetName() : "Questions",
                      Collectors.collectingAndThen(
                          Collectors.toList(),
                          list ->
                              list.stream()
                                  .sorted(Comparator.comparing(VantaQuestion::getRowIndex))
                                  .collect(Collectors.toList()))));

      // Create styles
      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle questionStyle = createQuestionStyle(workbook);
      CellStyle answerStyle = createAnswerStyle(workbook);
      CellStyle highConfStyle = createHighConfStyle(workbook);
      CellStyle lowConfStyle = createLowConfStyle(workbook);

      for (Map.Entry<String, List<VantaQuestion>> entry : questionsBySheet.entrySet()) {
        String sheetName = sanitizeSheetName(entry.getKey());
        Sheet sheet = workbook.createSheet(sheetName);

        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
          "Q#",
          "Category",
          "Sub-Category",
          "Question",
          "Answer",
          "Confidence",
          "Status",
          "Citations"
        };
        for (int i = 0; i < headers.length; i++) {
          Cell cell = headerRow.createCell(i);
          cell.setCellValue(headers[i]);
          cell.setCellStyle(headerStyle);
        }

        List<VantaQuestion> questions = entry.getValue();
        for (int i = 0; i < questions.size(); i++) {
          VantaQuestion q = questions.get(i);
          Row row = sheet.createRow(i + 1);

          // Q#
          Cell c0 = row.createCell(0);
          c0.setCellValue(q.getQuestionNumber());
          c0.setCellStyle(questionStyle);

          // Category
          Cell c1 = row.createCell(1);
          c1.setCellValue(q.getCategory() != null ? q.getCategory() : "");
          c1.setCellStyle(questionStyle);

          // Sub-Category
          Cell c2 = row.createCell(2);
          c2.setCellValue(q.getSubCategory() != null ? q.getSubCategory() : "");
          c2.setCellStyle(questionStyle);

          // Question
          Cell c3 = row.createCell(3);
          c3.setCellValue(q.getText());
          c3.setCellStyle(questionStyle);

          // Answer
          Cell c4 = row.createCell(4);
          String answer =
              q.getFinalAnswer() != null
                  ? q.getFinalAnswer()
                  : (q.getAiAnswer() != null ? q.getAiAnswer() : "");
          c4.setCellValue(answer);

          // Style based on confidence
          CellStyle ansStyle = questionStyle;
          if (q.getConfidenceScore() != null) {
            if (q.getConfidenceScore() >= 90) ansStyle = highConfStyle;
            else if (q.getConfidenceScore() < 70) ansStyle = lowConfStyle;
            else ansStyle = answerStyle;
          }
          c4.setCellStyle(ansStyle);

          // Confidence
          Cell c5 = row.createCell(5);
          c5.setCellValue(q.getConfidenceScore() != null ? q.getConfidenceScore() + "%" : "N/A");
          c5.setCellStyle(questionStyle);

          // Status
          Cell c6 = row.createCell(6);
          c6.setCellValue(q.getStatus().name());
          c6.setCellStyle(questionStyle);

          // Citations
          Cell c7 = row.createCell(7);
          String citations =
              q.getCitationIds() != null ? q.getCitationIds().size() + " source(s)" : "None";
          c7.setCellValue(citations);
          c7.setCellStyle(questionStyle);
        }

        // Auto-size columns
        for (int i = 0; i < 8; i++) {
          sheet.autoSizeColumn(i);
        }
      }

      workbook.write(baos);
      return baos.toByteArray();
    }
  }

  public byte[] exportExecutiveSummary(VantaQuestionnaire questionnaire) throws IOException {
    log.info("Generating executive summary for questionnaire {}", questionnaire.getId());

    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("Executive Summary");

      // Styles
      CellStyle titleStyle = createTitleStyle(workbook);
      CellStyle headingStyle = createHeadingStyle(workbook);
      CellStyle bodyStyle = createBodyStyle(workbook);
      CellStyle metricStyle = createMetricStyle(workbook);
      CellStyle lowConfStyle = createLowConfStyle(workbook);

      int rowIdx = 0;

      // Title
      Row titleRow = sheet.createRow(rowIdx++);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("Security Questionnaire Executive Summary");
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

      // Questionnaire Info
      rowIdx++;
      addInfoRow(
          sheet, rowIdx++, "Questionnaire:", questionnaire.getTitle(), headingStyle, bodyStyle);
      addInfoRow(
          sheet, rowIdx++, "Status:", questionnaire.getStatus().name(), headingStyle, bodyStyle);
      addInfoRow(
          sheet,
          rowIdx++,
          "Total Questions:",
          String.valueOf(questionnaire.getTotalQuestions()),
          headingStyle,
          bodyStyle);
      addInfoRow(
          sheet,
          rowIdx++,
          "Approved:",
          String.valueOf(questionnaire.getApprovedQuestions()),
          headingStyle,
          bodyStyle);
      addInfoRow(
          sheet,
          rowIdx++,
          "High Confidence:",
          String.valueOf(questionnaire.getHighConfidenceQuestions()),
          headingStyle,
          bodyStyle);
      addInfoRow(
          sheet,
          rowIdx++,
          "Needs Review:",
          String.valueOf(questionnaire.getNeedsReviewQuestions()),
          headingStyle,
          bodyStyle);
      addInfoRow(
          sheet,
          rowIdx++,
          "Completion:",
          String.format(
              "%.1f%%",
              questionnaire.getTotalQuestions() > 0
                  ? (questionnaire.getApprovedQuestions()
                      * 100.0
                      / questionnaire.getTotalQuestions())
                  : 0),
          headingStyle,
          metricStyle);

      rowIdx++; // Spacer

      // Metrics Table
      Row metricsHeader = sheet.createRow(rowIdx++);
      String[] metricHeaders = {"Metric", "Count", "Percentage"};
      for (int i = 0; i < metricHeaders.length; i++) {
        Cell cell = metricsHeader.createCell(i);
        cell.setCellValue(metricHeaders[i]);
        cell.setCellStyle(headingStyle);
      }

      Map<String, Long> statusCounts = new LinkedHashMap<>();
      statusCounts.put("Approved", (long) questionnaire.getApprovedQuestions());
      statusCounts.put("High Confidence (>90%)", (long) questionnaire.getHighConfidenceQuestions());
      statusCounts.put("Needs Review", (long) questionnaire.getNeedsReviewQuestions());

      for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
        Row r = sheet.createRow(rowIdx++);
        Cell c0 = r.createCell(0);
        c0.setCellValue(entry.getKey());
        c0.setCellStyle(bodyStyle);
        Cell c1 = r.createCell(1);
        c1.setCellValue(entry.getValue());
        c1.setCellStyle(bodyStyle);
        Cell c2 = r.createCell(2);
        double pct =
            questionnaire.getTotalQuestions() > 0
                ? (entry.getValue() * 100.0 / questionnaire.getTotalQuestions())
                : 0;
        c2.setCellValue(String.format("%.1f%%", pct));
        c2.setCellStyle(bodyStyle);
      }

      rowIdx += 2;

      // Top Questions Needing Attention
      Row attentionHeader = sheet.createRow(rowIdx++);
      Cell ah = attentionHeader.createCell(0);
      ah.setCellValue("Questions Requiring Attention");
      ah.setCellStyle(headingStyle);
      sheet.addMergedRegion(
          new org.apache.poi.ss.util.CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

      // Question detail table
      Row detailHeader = sheet.createRow(rowIdx++);
      String[] qHeaders = {"Q#", "Category", "Question", "Confidence", "Status", "AI Answer"};
      for (int i = 0; i < qHeaders.length; i++) {
        Cell cell = detailHeader.createCell(i);
        cell.setCellValue(qHeaders[i]);
        cell.setCellStyle(headingStyle);
      }

      // Sort questions by confidence (low first)
      List<VantaQuestion> sortedQuestions =
          questionnaire.getQuestions().stream()
              .sorted(
                  Comparator.comparingInt(
                      q -> q.getConfidenceScore() != null ? q.getConfidenceScore() : 100))
              .limit(20)
              .collect(Collectors.toList());

      for (VantaQuestion q : sortedQuestions) {
        Row r = sheet.createRow(rowIdx++);
        Cell c0 = r.createCell(0);
        c0.setCellValue(q.getQuestionNumber());
        c0.setCellStyle(bodyStyle);

        Cell c1 = r.createCell(1);
        c1.setCellValue(q.getCategory() != null ? q.getCategory() : "");
        c1.setCellStyle(bodyStyle);

        Cell c2 = r.createCell(2);
        c2.setCellValue(q.getText());
        c2.setCellStyle(bodyStyle);

        Cell c3 = r.createCell(3);
        c3.setCellValue(q.getConfidenceScore() != null ? q.getConfidenceScore() + "%" : "N/A");
        c3.setCellStyle(
            q.getConfidenceScore() != null && q.getConfidenceScore() < 70
                ? lowConfStyle
                : bodyStyle);

        Cell c4 = r.createCell(4);
        c4.setCellValue(q.getStatus().name());
        c4.setCellStyle(bodyStyle);

        Cell c5 = r.createCell(5);
        String aiAns = q.getAiAnswer() != null ? q.getAiAnswer() : "";
        c5.setCellValue(aiAns.length() > 200 ? aiAns.substring(0, 200) + "..." : aiAns);
        c5.setCellStyle(bodyStyle);
      }

      // Auto-size columns
      for (int i = 0; i < 6; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(baos);
      return baos.toByteArray();
    }
  }

  private Map<String, String> buildAnswerMap(VantaQuestionnaire questionnaire) {
    Map<String, String> map = new HashMap<>();
    for (VantaQuestion q : questionnaire.getQuestions()) {
      if (q.getFinalAnswer() != null && !q.getFinalAnswer().isEmpty()) {
        String key = buildKey(q.getSheetName(), q.getOriginalRowIndex());
        map.put(key, q.getFinalAnswer());
      }
    }
    return map;
  }

  private String buildKey(String sheetName, int rowIdx) {
    return sheetName + "|" + rowIdx;
  }

  private ParsedSheet parseSheetForExport(Sheet sheet, int sheetIdx) {
    // Simplified - in production would use VantaSpreadsheetParserService
    ParsedSheet parsed = new ParsedSheet();
    parsed.setSheetName(sheet.getSheetName());
    parsed.setSheetIndex(sheetIdx);

    // Find header row
    for (int i = 0; i < Math.min(3, sheet.getLastRowNum() + 1); i++) {
      Row row = sheet.getRow(i);
      if (row != null && hasHeaderKeywords(row)) {
        parsed.setHeaderRowIndex(i);
        break;
      }
    }
    return parsed;
  }

  private int findAnswerColumn(Sheet sheet, int headerRowIdx) {
    Row headerRow = sheet.getRow(headerRowIdx);
    if (headerRow == null) return -1;

    for (Cell cell : headerRow) {
      String header = getCellStringValue(cell).toLowerCase();
      if (header.contains("answer")
          || header.contains("response")
          || header.contains("reply")
          || header.contains("vendor")) {
        return cell.getColumnIndex();
      }
    }
    return -1;
  }

  private void setCellValuePreservingStyle(Cell cell, String value) {
    CellStyle originalStyle = cell.getCellStyle();
    cell.setCellValue(value);
    // Style is preserved automatically
  }

  private String getCellStringValue(Cell cell) {
    if (cell == null) return null;
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC ->
          DateUtil.isCellDateFormatted(cell)
              ? cell.getDateCellValue().toString()
              : String.valueOf((long) cell.getNumericCellValue());
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> cell.getCellFormula();
      default -> "";
    };
  }

  private boolean hasHeaderKeywords(Row row) {
    for (Cell cell : row) {
      String value = getCellStringValue(cell).toLowerCase();
      if (value.contains("question")
          || value.contains("category")
          || value.contains("answer")
          || value.contains("section")
          || value.contains("control")
          || value.contains("requirement")) {
        return true;
      }
    }
    return false;
  }

  private boolean isEmptyRow(Row row) {
    for (int i = 0; i < row.getLastCellNum(); i++) {
      Cell cell = row.getCell(i);
      if (cell != null && cell.getCellType() != CellType.BLANK) {
        String val = getCellStringValue(cell);
        if (val != null && !val.trim().isEmpty()) return false;
      }
    }
    return true;
  }

  private String sanitizeSheetName(String name) {
    if (name == null) return "Sheet";
    return name.replaceAll("[\\[\\]*:?/\\\\]", "").substring(0, Math.min(31, name.length()));
  }

  private void addInfoRow(
      Sheet sheet,
      int rowIdx,
      String label,
      String value,
      CellStyle labelStyle,
      CellStyle valueStyle) {
    Row row = sheet.createRow(rowIdx);
    Cell c0 = row.createCell(0);
    c0.setCellValue(label);
    c0.setCellStyle(labelStyle);
    Cell c1 = row.createCell(1);
    c1.setCellValue(value);
    c1.setCellStyle(valueStyle);
    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx, rowIdx, 1, 3));
  }

  // Style creation methods
  private CellStyle createHeaderStyle(Workbook wb) {
    CellStyle style = wb.createCellStyle();
    Font font = wb.createFont();
    font.setBold(true);
    font.setColor(IndexedColors.WHITE.getIndex());
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
    style.setAlignment(HorizontalAlignment.CENTER);
    return style;
  }

  private CellStyle createQuestionStyle(Workbook wb) {
    CellStyle style = wb.createCellStyle();
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
    style.setWrapText(true);
    style.setVerticalAlignment(VerticalAlignment.TOP);
    return style;
  }

  private CellStyle createAnswerStyle(Workbook wb) {
    CellStyle style = createQuestionStyle(wb);
    style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    return style;
  }

  private CellStyle createHighConfStyle(Workbook wb) {
    CellStyle style = createQuestionStyle(wb);
    style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    return style;
  }

  private CellStyle createLowConfStyle(Workbook wb) {
    CellStyle style = createQuestionStyle(wb);
    style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    return style;
  }

  private CellStyle createTitleStyle(Workbook wb) {
    CellStyle style = wb.createCellStyle();
    Font font = wb.createFont();
    font.setBold(true);
    font.setFontHeightInPoints((short) 16);
    font.setColor(IndexedColors.DARK_BLUE.getIndex());
    style.setFont(font);
    style.setAlignment(HorizontalAlignment.CENTER);
    return style;
  }

  private CellStyle createHeadingStyle(Workbook wb) {
    CellStyle style = wb.createCellStyle();
    Font font = wb.createFont();
    font.setBold(true);
    font.setFontHeightInPoints((short) 11);
    style.setFont(font);
    style.setBorderBottom(BorderStyle.THIN);
    return style;
  }

  private CellStyle createBodyStyle(Workbook wb) {
    CellStyle style = wb.createCellStyle();
    style.setBorderBottom(BorderStyle.THIN);
    style.setWrapText(true);
    style.setVerticalAlignment(VerticalAlignment.TOP);
    return style;
  }

  private CellStyle createMetricStyle(Workbook wb) {
    CellStyle style = createHeadingStyle(wb);
    Font font = wb.createFont();
    font.setColor(IndexedColors.DARK_GREEN.getIndex());
    font.setBold(true);
    style.setFont(font);
    return style;
  }

  // Inner class for parsed sheet
  private static class ParsedSheet {
    private String sheetName;
    private int sheetIndex;
    private int headerRowIndex = 0;

    public String getSheetName() {
      return sheetName;
    }

    public void setSheetName(String sheetName) {
      this.sheetName = sheetName;
    }

    public int getSheetIndex() {
      return sheetIndex;
    }

    public void setSheetIndex(int sheetIndex) {
      this.sheetIndex = sheetIndex;
    }

    public int getHeaderRowIndex() {
      return headerRowIndex;
    }

    public void setHeaderRowIndex(int headerRowIndex) {
      this.headerRowIndex = headerRowIndex;
    }
  }
}
