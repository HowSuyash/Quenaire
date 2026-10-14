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

import com.quenaire.domain.questionnaire.vanta.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VantaSpreadsheetParserService {

  private static final Logger log = LoggerFactory.getLogger(VantaSpreadsheetParserService.class);

  private static final Pattern QUESTION_NUMBER_PATTERN =
      Pattern.compile(
          "^(?:Q|Question|#|Num|Number|ID)[\\s\\-_]*(\\d+[\\.\\d]*)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CATEGORY_PATTERN =
      Pattern.compile(
          "^(?:Category|Section|Domain|Control|Area|Topic)[\\s\\-_]*(.*)$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern QUESTION_TEXT_PATTERN =
      Pattern.compile(
          "^(?:Question|Query|Requirement|Control|Requirement Text|Description)[\\s\\-_]*(.*)$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ANSWER_PATTERN =
      Pattern.compile(
          "^(?:Answer|Response|Reply|Your Answer|Vendor Answer)[\\s\\-_]*(.*)$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern NOTES_PATTERN =
      Pattern.compile(
          "^(?:Notes|Comments|Remarks|Evidence|Reference|Guidance)[\\s\\-_]*(.*)$",
          Pattern.CASE_INSENSITIVE);

  public ParsedQuestionnaire parse(MultipartFile file) throws IOException {
    try (InputStream is = file.getInputStream();
        Workbook workbook = new XSSFWorkbook(is)) {

      ParsedQuestionnaire result = new ParsedQuestionnaire();
      result.setFileName(file.getOriginalFilename());
      result.setTotalSheets(workbook.getNumberOfSheets());

      for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
        Sheet sheet = workbook.getSheetAt(sheetIdx);
        if (sheet.getPhysicalNumberOfRows() < 2) continue;

        ParsedSheet parsedSheet = parseSheet(sheet, sheetIdx);
        if (!parsedSheet.getQuestions().isEmpty()) {
          result.getSheets().add(parsedSheet);
        }
      }

      result.setTotalQuestions(
          result.getSheets().stream().mapToInt(s -> s.getQuestions().size()).sum());

      return result;
    }
  }

  private ParsedSheet parseSheet(Sheet sheet, int sheetIndex) {
    String sheetName = sheet.getSheetName();
    log.debug("Parsing sheet: {} (index {})", sheetName, sheetIndex);

    ParsedSheet parsedSheet = new ParsedSheet();
    parsedSheet.setSheetName(sheetName);
    parsedSheet.setSheetIndex(sheetIndex);

    // Detect header row (usually row 0 or 1)
    int headerRowIndex = detectHeaderRow(sheet);
    Row headerRow = sheet.getRow(headerRowIndex);

    if (headerRow == null) {
      log.warn("No header row found in sheet: {}", sheetName);
      return parsedSheet;
    }

    // Detect column mappings
    ColumnMapping mapping = detectColumnMapping(headerRow);
    if (mapping.getQuestionTextCol() < 0) {
      mapping.setQuestionTextCol(guessQuestionTextColumn(sheet, headerRowIndex, mapping));
    }

    // Parse data rows
    for (int rowIdx = headerRowIndex + 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
      Row row = sheet.getRow(rowIdx);
      if (row == null || isEmptyRow(row)) continue;

      ParsedQuestion question = parseQuestionRow(row, rowIdx, headerRowIndex, mapping, sheetName);
      if (question != null) {
        parsedSheet.getQuestions().add(question);
      }
    }

    // Store sheet style info for round-trip export
    parsedSheet.setStyleData(extractSheetStyleData(sheet));

    return parsedSheet;
  }

  private int detectHeaderRow(Sheet sheet) {
    // Check first 3 rows for header-like content
    for (int i = 0; i < Math.min(3, sheet.getLastRowNum() + 1); i++) {
      Row row = sheet.getRow(i);
      if (row != null && hasHeaderKeywords(row)) {
        return i;
      }
    }
    return 0; // Default to first row
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

  /**
   * Which column holds what, decided from the header row.
   *
   * <p>Headers are compared word by word and the most specific rule is tried first. This used to
   * be substring matching with the question-number rule first, which put the column most
   * questionnaires call simply "Question" into the number slot -- leaving no text column, so every
   * row was skipped and the upload produced zero questions. The same substring test found "id"
   * inside "Evidence", "Guidance" and "Valid values". The first column to claim a role keeps it.
   */
  private ColumnMapping detectColumnMapping(Row headerRow) {
    ColumnMapping mapping = new ColumnMapping();

    for (int colIdx = 0; colIdx < headerRow.getLastCellNum(); colIdx++) {
      Cell cell = headerRow.getCell(colIdx);
      if (cell == null) continue;

      String raw = getCellStringValue(cell);
      if (raw == null || raw.isBlank()) continue;

      String header = normalizeHeader(raw);
      Set<String> words = new HashSet<>(Arrays.asList(header.split(" ")));

      if (header.contains("sub category")
          || header.contains("subcategory")
          || header.contains("sub section")
          || header.contains("subsection")
          || header.contains("sub domain")) {
        if (mapping.getSubCategoryCol() < 0) mapping.setSubCategoryCol(colIdx);
      } else if (hasAny(words, "options", "choices")
          || header.contains("valid values")
          || header.contains("valid responses")
          || header.contains("allowed values")
          || header.contains("possible answers")) {
        if (mapping.getOptionsCol() < 0) mapping.setOptionsCol(colIdx);
      } else if (header.equals("type")
          || header.equals("format")
          || header.equals("qtype")
          || header.endsWith(" type")) {
        if (mapping.getTypeCol() < 0) mapping.setTypeCol(colIdx);
      } else if (isNumberHeader(header, words)) {
        if (mapping.getQuestionNumberCol() < 0) mapping.setQuestionNumberCol(colIdx);
      } else if (hasAny(words, "answer", "answers", "response", "responses", "reply")) {
        if (mapping.getAnswerCol() < 0) mapping.setAnswerCol(colIdx);
      } else if (hasAny(
          words,
          "notes",
          "note",
          "comments",
          "comment",
          "remarks",
          "evidence",
          "reference",
          "references",
          "guidance",
          "explanation",
          "justification")) {
        if (mapping.getNotesCol() < 0) mapping.setNotesCol(colIdx);
      } else if (hasAny(
          words, "attachment", "attachments", "attach", "file", "upload", "document")) {
        // Ahead of "required": "Attachment required" says whether to attach, not whether
        // the question must be answered.
        if (mapping.getAttachmentCol() < 0) mapping.setAttachmentCol(colIdx);
      } else if (hasAny(words, "required", "mandatory", "obligatory")) {
        if (mapping.getRequiredCol() < 0) mapping.setRequiredCol(colIdx);
      } else if (hasAny(
              words, "question", "questions", "query", "requirement", "requirements", "description")
          || header.contains("control text")
          || header.contains("control statement")) {
        if (mapping.getQuestionTextCol() < 0) mapping.setQuestionTextCol(colIdx);
      } else if (hasAny(
          words, "category", "section", "domain", "control", "area", "topic", "group", "family")) {
        if (mapping.getCategoryCol() < 0) mapping.setCategoryCol(colIdx);
      }
    }

    log.debug("Detected column mapping: {}", mapping);
    return mapping;
  }

  /** Lower case, punctuation other than '#' turned to spaces, whitespace collapsed. */
  private static String normalizeHeader(String header) {
    return header
        .toLowerCase(Locale.ROOT)
        .replace("#", " # ")
        .replaceAll("[^a-z0-9#]+", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  /**
   * "#", "No.", "ID", "Ref", "Question ID", "Control No", "Item number", "Q#" -- an identifier
   * column. Judged on whole words: "Evidence provided" contains "id" but is not an id.
   */
  private static boolean isNumberHeader(String header, Set<String> words) {
    if (words.contains("#")) return true;
    if (Set.of("id", "no", "num", "number", "ref", "item", "q", "s no", "sr no").contains(header)) {
      return true;
    }
    String last = header.substring(header.lastIndexOf(' ') + 1);
    return header.contains(" ") && Set.of("id", "no", "num", "number", "ref").contains(last);
  }

  private static boolean hasAny(Set<String> words, String... candidates) {
    for (String candidate : candidates) {
      if (words.contains(candidate)) return true;
    }
    return false;
  }

  /**
   * The column holding the questions when no header said so: the one whose cells carry the most
   * text. A questionnaire whose question column is headed something unrecognised, or not headed at
   * all, would otherwise yield nothing.
   */
  private int guessQuestionTextColumn(Sheet sheet, int headerRowIndex, ColumnMapping mapping) {
    Set<Integer> taken =
        Stream.of(
            mapping.getQuestionNumberCol(),
            mapping.getCategoryCol(),
            mapping.getSubCategoryCol(),
            mapping.getAnswerCol(),
            mapping.getNotesCol(),
            mapping.getTypeCol(),
            mapping.getOptionsCol(),
            mapping.getRequiredCol(),
            mapping.getAttachmentCol())
            .filter(col -> col >= 0)
            .collect(Collectors.toSet());

    Map<Integer, Integer> totalLength = new HashMap<>();
    int lastSampled = Math.min(sheet.getLastRowNum(), headerRowIndex + 25);
    for (int rowIdx = headerRowIndex + 1; rowIdx <= lastSampled; rowIdx++) {
      Row row = sheet.getRow(rowIdx);
      if (row == null) continue;
      for (Cell cell : row) {
        if (taken.contains(cell.getColumnIndex()) || cell.getCellType() != CellType.STRING) {
          continue;
        }
        totalLength.merge(
            cell.getColumnIndex(), cell.getStringCellValue().trim().length(), Integer::sum);
      }
    }

    // A column of short codes or yes/no values is not a question column, however long it is.
    int sampledRows = Math.max(1, lastSampled - headerRowIndex);
    return totalLength.entrySet().stream()
        .filter(entry -> entry.getValue() / sampledRows >= 15)
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(-1);
  }

  private ParsedQuestion parseQuestionRow(
      Row row, int rowIdx, int headerRowIdx, ColumnMapping mapping, String sheetName) {
    String questionText = getCellValue(row, mapping.getQuestionTextCol());
    if (questionText == null || questionText.trim().isEmpty()) {
      return null; // Skip empty questions
    }

    ParsedQuestion question = new ParsedQuestion();
    question.setRowIndex(rowIdx);
    question.setSheetName(sheetName);

    // Question number
    String qNum = getCellValue(row, mapping.getQuestionNumberCol());
    question.setQuestionNumber(qNum != null ? qNum : String.valueOf(rowIdx));

    // Category
    question.setCategory(getCellValue(row, mapping.getCategoryCol()));
    question.setSubCategory(getCellValue(row, mapping.getSubCategoryCol()));

    // Question text
    question.setText(questionText.trim());

    // Answer (existing)
    String answer = getCellValue(row, mapping.getAnswerCol());
    question.setExistingAnswer(answer);

    // Notes
    question.setNotes(getCellValue(row, mapping.getNotesCol()));

    // Question type detection
    QuestionType detectedType = detectQuestionType(row, mapping);
    question.setQuestionType(detectedType);

    // Options
    String options = getCellValue(row, mapping.getOptionsCol());
    if (options != null) {
      question.setOptions(parseOptions(options, detectedType));
    } else if (detectedType.hasOptions()) {
      question.setOptions(Arrays.asList(detectedType.getDefaultOptions()));
    }

    // Required
    question.setRequired(parseBoolean(getCellValue(row, mapping.getRequiredCol())));

    // Attachment request
    question.setAttachmentRequest(parseBoolean(getCellValue(row, mapping.getAttachmentCol())));

    // Preserve cell coordinates for round-trip
    question.setColIndex(mapping.getQuestionTextCol());
    question.setOriginalRowIndex(rowIdx);
    question.setOriginalHeaderRowIndex(headerRowIdx);

    // Extract cell style for round-trip
    Cell questionCell = row.getCell(mapping.getQuestionTextCol());
    if (questionCell != null) {
      question.setCellStyleData(extractCellStyle(questionCell));
    }

    return question;
  }

  private QuestionType detectQuestionType(Row row, ColumnMapping mapping) {
    // Check explicit type column
    if (mapping.getTypeCol() >= 0) {
      String typeStr = getCellValue(row, mapping.getTypeCol());
      if (typeStr != null) {
        QuestionType type = parseQuestionType(typeStr);
        if (type != QuestionType.FREE_TEXT) return type;
      }
    }

    // Check for dropdown validation. Many questionnaires have no answer column at all -- the
    // answers are what we produce -- and POI throws on a negative index rather than returning null.
    Cell answerCell = mapping.getAnswerCol() >= 0 ? row.getCell(mapping.getAnswerCol()) : null;
    if (answerCell != null && hasDataValidation(answerCell)) {
      return inferTypeFromValidation(answerCell);
    }

    // Check options column
    if (mapping.getOptionsCol() >= 0) {
      String options = getCellValue(row, mapping.getOptionsCol());
      if (options != null && !options.trim().isEmpty()) {
        return inferTypeFromOptions(options);
      }
    }

    // Infer from answer content
    String answer = getCellValue(row, mapping.getAnswerCol());
    if (answer != null) {
      return inferTypeFromAnswer(answer);
    }

    return QuestionType.FREE_TEXT;
  }

  private boolean hasDataValidation(Cell cell) {
    // Check for data validation (dropdown)
    // This is a simplified check - in reality you'd need to check the sheet's data validations
    return cell.getCellStyle().getDataFormat() != 0; // Simplified
  }

  private QuestionType inferTypeFromValidation(Cell cell) {
    // Would check the actual data validation rules
    return QuestionType.SINGLE_CHOICE;
  }

  private QuestionType inferTypeFromOptions(String options) {
    List<String> opts = parseOptions(options, QuestionType.SINGLE_CHOICE);
    if (opts.size() == 2) {
      String opt1 = opts.get(0).toLowerCase();
      String opt2 = opts.get(1).toLowerCase();
      if ((opt1.equals("yes") && opt2.equals("no")) || (opt1.equals("y") && opt2.equals("n"))) {
        return QuestionType.YES_NO;
      }
      if (opts.contains("n/a") || opts.contains("na")) {
        return QuestionType.YES_NO_NA;
      }
      if (opts.contains("partial")) {
        return QuestionType.YES_NO_PARTIAL;
      }
    }
    return opts.size() > 2 ? QuestionType.MULTIPLE_CHOICE : QuestionType.SINGLE_CHOICE;
  }

  private QuestionType inferTypeFromAnswer(String answer) {
    String lower = answer.toLowerCase().trim();
    if (lower.equals("yes") || lower.equals("no") || lower.equals("y") || lower.equals("n")) {
      return QuestionType.YES_NO;
    }
    if (lower.equals("true") || lower.equals("false")) {
      return QuestionType.YES_NO;
    }
    if (lower.matches("^\\d+$")) {
      return QuestionType.NUMERIC;
    }
    if (lower.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
      return QuestionType.DATE;
    }
    return QuestionType.FREE_TEXT;
  }

  private QuestionType parseQuestionType(String typeStr) {
    String lower = typeStr.toLowerCase().trim();
    for (QuestionType type : QuestionType.values()) {
      if (type.getCode().equals(lower) || type.getDisplayName().toLowerCase().contains(lower)) {
        return type;
      }
    }
    return QuestionType.FREE_TEXT;
  }

  private List<String> parseOptions(String options, QuestionType type) {
    if (options == null) return new ArrayList<>();
    return Arrays.stream(options.split("[,;|]"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private Boolean parseBoolean(String value) {
    if (value == null) return false;
    String lower = value.toLowerCase().trim();
    return lower.equals("true")
        || lower.equals("yes")
        || lower.equals("y")
        || lower.equals("1")
        || lower.equals("required");
  }

  private String getCellValue(Row row, int colIdx) {
    if (colIdx < 0) return null;
    Cell cell = row.getCell(colIdx);
    return cell != null ? getCellStringValue(cell) : null;
  }

  private String getCellStringValue(Cell cell) {
    if (cell == null) return null;
    return switch (cell.getCellType()) {
      case STRING -> cell.getStringCellValue();
      case NUMERIC -> {
        if (DateUtil.isCellDateFormatted(cell)) {
          yield cell.getDateCellValue().toString();
        }
        yield String.valueOf((long) cell.getNumericCellValue());
      }
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> cell.getCellFormula();
      default -> "";
    };
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

  private SheetStyleData extractSheetStyleData(Sheet sheet) {
    SheetStyleData styleData = new SheetStyleData();
    // Extract column widths
    for (int i = 0; i < 50; i++) {
      styleData.setColumnWidth(i, sheet.getColumnWidth(i));
    }
    return styleData;
  }

  private Map<String, Object> extractCellStyle(Cell cell) {
    Map<String, Object> style = new HashMap<>();
    CellStyle cs = cell.getCellStyle();
    style.put("fontBold", cs.getFontIndex() > 0); // Simplified
    style.put("alignment", cs.getAlignment().toString());
    style.put("borderTop", cs.getBorderTop().toString());
    style.put("borderBottom", cs.getBorderBottom().toString());
    style.put("fillPattern", cs.getFillPattern().toString());
    return style;
  }

  // Inner classes for parsed data
  public static class ParsedQuestionnaire {
    private String fileName;
    private int totalSheets;
    private int totalQuestions;
    private List<ParsedSheet> sheets = new ArrayList<>();

    // Getters/Setters
    public String getFileName() {
      return fileName;
    }

    public void setFileName(String fileName) {
      this.fileName = fileName;
    }

    public int getTotalSheets() {
      return totalSheets;
    }

    public void setTotalSheets(int totalSheets) {
      this.totalSheets = totalSheets;
    }

    public int getTotalQuestions() {
      return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
      this.totalQuestions = totalQuestions;
    }

    public List<ParsedSheet> getSheets() {
      return sheets;
    }

    public void setSheets(List<ParsedSheet> sheets) {
      this.sheets = sheets;
    }
  }

  public static class ParsedSheet {
    private String sheetName;
    private int sheetIndex;
    private List<ParsedQuestion> questions = new ArrayList<>();
    private SheetStyleData styleData;

    // Getters/Setters
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

    public List<ParsedQuestion> getQuestions() {
      return questions;
    }

    public void setQuestions(List<ParsedQuestion> questions) {
      this.questions = questions;
    }

    public SheetStyleData getStyleData() {
      return styleData;
    }

    public void setStyleData(SheetStyleData styleData) {
      this.styleData = styleData;
    }
  }

  public static class ParsedQuestion {
    private String questionNumber;
    private String category;
    private String subCategory;
    private String text;
    private String existingAnswer;
    private String notes;
    private QuestionType questionType = QuestionType.FREE_TEXT;
    private List<String> options = new ArrayList<>();
    private Boolean required = false;
    private Boolean attachmentRequest = false;
    private int rowIndex;
    private int colIndex;
    private int originalRowIndex;
    private int originalHeaderRowIndex;
    private String sheetName;
    private Map<String, Object> cellStyleData;
    private Map<String, Object> cellCoordinates;

    // Getters/Setters
    public String getQuestionNumber() {
      return questionNumber;
    }

    public void setQuestionNumber(String questionNumber) {
      this.questionNumber = questionNumber;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public String getSubCategory() {
      return subCategory;
    }

    public void setSubCategory(String subCategory) {
      this.subCategory = subCategory;
    }

    public String getText() {
      return text;
    }

    public void setText(String text) {
      this.text = text;
    }

    public String getExistingAnswer() {
      return existingAnswer;
    }

    public void setExistingAnswer(String existingAnswer) {
      this.existingAnswer = existingAnswer;
    }

    public String getNotes() {
      return notes;
    }

    public void setNotes(String notes) {
      this.notes = notes;
    }

    public QuestionType getQuestionType() {
      return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
      this.questionType = questionType;
    }

    public List<String> getOptions() {
      return options;
    }

    public void setOptions(List<String> options) {
      this.options = options;
    }

    public Boolean getRequired() {
      return required;
    }

    public void setRequired(Boolean required) {
      this.required = required;
    }

    public Boolean getAttachmentRequest() {
      return attachmentRequest;
    }

    public void setAttachmentRequest(Boolean attachmentRequest) {
      this.attachmentRequest = attachmentRequest;
    }

    public int getRowIndex() {
      return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
      this.rowIndex = rowIndex;
    }

    public int getColIndex() {
      return colIndex;
    }

    public void setColIndex(int colIndex) {
      this.colIndex = colIndex;
    }

    public int getOriginalRowIndex() {
      return originalRowIndex;
    }

    public void setOriginalRowIndex(int originalRowIndex) {
      this.originalRowIndex = originalRowIndex;
    }

    public int getOriginalHeaderRowIndex() {
      return originalHeaderRowIndex;
    }

    public void setOriginalHeaderRowIndex(int originalHeaderRowIndex) {
      this.originalHeaderRowIndex = originalHeaderRowIndex;
    }

    public String getSheetName() {
      return sheetName;
    }

    public void setSheetName(String sheetName) {
      this.sheetName = sheetName;
    }

    public Map<String, Object> getCellStyleData() {
      return cellStyleData;
    }

    public void setCellStyleData(Map<String, Object> cellStyleData) {
      this.cellStyleData = cellStyleData;
    }

    public Map<String, Object> getCellCoordinates() {
      return cellCoordinates;
    }

    public void setCellCoordinates(Map<String, Object> cellCoordinates) {
      this.cellCoordinates = cellCoordinates;
    }
  }

  public static class ColumnMapping {
    private int questionNumberCol = -1;
    private int categoryCol = -1;
    private int subCategoryCol = -1;
    private int questionTextCol = -1;
    private int answerCol = -1;
    private int notesCol = -1;
    private int typeCol = -1;
    private int optionsCol = -1;
    private int requiredCol = -1;
    private int attachmentCol = -1;

    // Getters/Setters
    public int getQuestionNumberCol() {
      return questionNumberCol;
    }

    public void setQuestionNumberCol(int questionNumberCol) {
      this.questionNumberCol = questionNumberCol;
    }

    public int getCategoryCol() {
      return categoryCol;
    }

    public void setCategoryCol(int categoryCol) {
      this.categoryCol = categoryCol;
    }

    public int getSubCategoryCol() {
      return subCategoryCol;
    }

    public void setSubCategoryCol(int subCategoryCol) {
      this.subCategoryCol = subCategoryCol;
    }

    public int getQuestionTextCol() {
      return questionTextCol;
    }

    public void setQuestionTextCol(int questionTextCol) {
      this.questionTextCol = questionTextCol;
    }

    public int getAnswerCol() {
      return answerCol;
    }

    public void setAnswerCol(int answerCol) {
      this.answerCol = answerCol;
    }

    public int getNotesCol() {
      return notesCol;
    }

    public void setNotesCol(int notesCol) {
      this.notesCol = notesCol;
    }

    public int getTypeCol() {
      return typeCol;
    }

    public void setTypeCol(int typeCol) {
      this.typeCol = typeCol;
    }

    public int getOptionsCol() {
      return optionsCol;
    }

    public void setOptionsCol(int optionsCol) {
      this.optionsCol = optionsCol;
    }

    public int getRequiredCol() {
      return requiredCol;
    }

    public void setRequiredCol(int requiredCol) {
      this.requiredCol = requiredCol;
    }

    public int getAttachmentCol() {
      return attachmentCol;
    }

    public void setAttachmentCol(int attachmentCol) {
      this.attachmentCol = attachmentCol;
    }

    @Override
    public String toString() {
      return "ColumnMapping{"
          + "questionNumberCol="
          + questionNumberCol
          + ", categoryCol="
          + categoryCol
          + ", questionTextCol="
          + questionTextCol
          + ", answerCol="
          + answerCol
          + ", notesCol="
          + notesCol
          + ", typeCol="
          + typeCol
          + ", optionsCol="
          + optionsCol
          + ", requiredCol="
          + requiredCol
          + ", attachmentCol="
          + attachmentCol
          + '}';
    }
  }

  public static class SheetStyleData {
    private Map<Integer, Integer> columnWidths = new HashMap<>();
    private Map<Integer, Map<String, Object>> rowStyles = new HashMap<>();

    public void setColumnWidth(int colIdx, int width) {
      columnWidths.put(colIdx, width);
    }

    public Integer getColumnWidth(int colIdx) {
      return columnWidths.get(colIdx);
    }
  }

  /** Parse spreadsheet from byte array */
  public ParsedQuestionnaire parseBytes(byte[] fileBytes, String fileName) throws IOException {
    try (InputStream is = new java.io.ByteArrayInputStream(fileBytes);
        Workbook workbook = new XSSFWorkbook(is)) {

      ParsedQuestionnaire result = new ParsedQuestionnaire();
      result.setFileName(fileName);
      result.setTotalSheets(workbook.getNumberOfSheets());

      for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
        Sheet sheet = workbook.getSheetAt(sheetIdx);
        if (sheet.getPhysicalNumberOfRows() < 2) continue;

        ParsedSheet parsedSheet = parseSheet(sheet, sheetIdx);
        if (!parsedSheet.getQuestions().isEmpty()) {
          result.getSheets().add(parsedSheet);
        }
      }

      result.setTotalQuestions(
          result.getSheets().stream().mapToInt(s -> s.getQuestions().size()).sum());

      return result;
    }
  }
}
