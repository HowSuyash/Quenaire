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

import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Renders a completed questionnaire as a branded PDF — the artifact most customers actually forward
 * to their prospect.
 *
 * <p>PDFBox has no layout engine, so this class does its own line wrapping and pagination.
 * Everything is measured in PDF points from the bottom-left origin.
 */
@Service
public class VantaPdfExportService {

  private static final Logger log = LoggerFactory.getLogger(VantaPdfExportService.class);

  private static final PDRectangle PAGE_SIZE = PDRectangle.LETTER;
  private static final float MARGIN = 54f; // 0.75 inch
  private static final float BODY_SIZE = 10f;
  private static final float LEADING = 13f;
  private static final float SECTION_GAP = 16f;

  private static final PDFont REGULAR = PDType1Font.HELVETICA;
  private static final PDFont BOLD = PDType1Font.HELVETICA_BOLD;
  private static final PDFont ITALIC = PDType1Font.HELVETICA_OBLIQUE;

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a");

  /** Branding supplied by the tenant; nulls fall back to Quenaire defaults. */
  public record Branding(String companyName, String accentHex, String footerNote) {
    public static Branding defaults() {
      return new Branding("Quenaire", "#2563EB", null);
    }
  }

  public byte[] exportQuestionnaire(
      VantaQuestionnaire questionnaire, List<VantaQuestion> questions, Branding branding)
      throws IOException {
    Branding brand = branding != null ? branding : Branding.defaults();
    Color accent = parseColor(brand.accentHex());

    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      Renderer renderer = new Renderer(document, brand, accent);

      renderer.startPage();
      renderer.drawCoverBlock(questionnaire, questions);

      String currentSection = null;
      int index = 0;

      for (VantaQuestion question : questions) {
        String section = sectionOf(question);
        if (!section.equals(currentSection)) {
          currentSection = section;
          renderer.drawSectionHeading(section);
        }
        renderer.drawQuestion(++index, question);
      }

      renderer.finish();
      document.save(out);

      log.info(
          "Exported questionnaire {} to PDF ({} questions)",
          questionnaire.getId(),
          questions.size());
      return out.toByteArray();
    }
  }

  private static String sectionOf(VantaQuestion question) {
    if (question.getSection() != null && !question.getSection().isBlank())
      return question.getSection();
    if (question.getCategory() != null && !question.getCategory().isBlank())
      return question.getCategory();
    return "General";
  }

  private static Color parseColor(String hex) {
    try {
      if (hex == null) return new Color(0x25, 0x63, 0xEB);
      String clean = hex.startsWith("#") ? hex.substring(1) : hex;
      return new Color(Integer.parseInt(clean, 16));
    } catch (NumberFormatException e) {
      return new Color(0x25, 0x63, 0xEB);
    }
  }

  /**
   * Stateful cursor over the document. Keeps the "where am I on the page" bookkeeping out of the
   * export logic above.
   */
  private static final class Renderer {

    private final PDDocument document;
    private final Branding branding;
    private final Color accent;
    private final float contentWidth;

    private PDPage page;
    private PDPageContentStream stream;
    private float y;
    private int pageNumber;

    Renderer(PDDocument document, Branding branding, Color accent) {
      this.document = document;
      this.branding = branding;
      this.accent = accent;
      this.contentWidth = PAGE_SIZE.getWidth() - (2 * MARGIN);
    }

    void startPage() throws IOException {
      closeStream();

      page = new PDPage(PAGE_SIZE);
      document.addPage(page);
      stream = new PDPageContentStream(document, page);
      pageNumber++;
      y = PAGE_SIZE.getHeight() - MARGIN;

      drawHeaderRule();
      drawFooter();
    }

    private void drawHeaderRule() throws IOException {
      stream.setNonStrokingColor(accent);
      stream.addRect(0, PAGE_SIZE.getHeight() - 8f, PAGE_SIZE.getWidth(), 8f);
      stream.fill();
      stream.setNonStrokingColor(Color.BLACK);
      y -= 12f;
    }

    private void drawFooter() throws IOException {
      String left = branding.companyName() != null ? branding.companyName() : "Quenaire";
      String right = "Page " + pageNumber;

      stream.setNonStrokingColor(new Color(0x94, 0xA3, 0xB8));
      stream.beginText();
      stream.setFont(REGULAR, 8f);
      stream.newLineAtOffset(MARGIN, MARGIN - 22f);
      stream.showText(sanitize(left));
      stream.endText();

      float rightWidth = textWidth(REGULAR, 8f, right);
      stream.beginText();
      stream.setFont(REGULAR, 8f);
      stream.newLineAtOffset(PAGE_SIZE.getWidth() - MARGIN - rightWidth, MARGIN - 22f);
      stream.showText(right);
      stream.endText();

      if (branding.footerNote() != null && !branding.footerNote().isBlank()) {
        String note = sanitize(branding.footerNote());
        float noteWidth = textWidth(REGULAR, 8f, note);
        stream.beginText();
        stream.setFont(REGULAR, 8f);
        stream.newLineAtOffset((PAGE_SIZE.getWidth() - noteWidth) / 2f, MARGIN - 22f);
        stream.showText(note);
        stream.endText();
      }

      stream.setNonStrokingColor(Color.BLACK);
    }

    /** Start a new page when {@code needed} points would overflow the body area. */
    private void ensureSpace(float needed) throws IOException {
      if (y - needed < MARGIN) {
        startPage();
      }
    }

    void drawCoverBlock(VantaQuestionnaire questionnaire, List<VantaQuestion> questions)
        throws IOException {
      drawText(sanitize(questionnaire.getTitle()), BOLD, 20f, Color.BLACK);
      y -= 4f;

      if (questionnaire.getDescription() != null && !questionnaire.getDescription().isBlank()) {
        drawWrapped(
            sanitize(questionnaire.getDescription()),
            REGULAR,
            BODY_SIZE,
            new Color(0x47, 0x55, 0x69));
      }

      y -= 6f;

      long approved =
          questions.stream()
              .filter(q -> q.getStatus() == VantaQuestion.QuestionStatus.APPROVED)
              .count();
      long highConfidence =
          questions.stream()
              .filter(q -> q.getConfidenceScore() != null && q.getConfidenceScore() >= 90)
              .count();

      drawMetaRow("Generated", OffsetDateTime.now().format(STAMP));
      drawMetaRow("Questions", String.valueOf(questions.size()));
      drawMetaRow("Approved", approved + " of " + questions.size());
      drawMetaRow("High confidence", String.valueOf(highConfidence));
      if (questionnaire.getFileName() != null) {
        drawMetaRow("Source file", sanitize(questionnaire.getFileName()));
      }

      y -= SECTION_GAP;
      drawDivider();
    }

    private void drawMetaRow(String label, String value) throws IOException {
      ensureSpace(LEADING);
      stream.beginText();
      stream.setFont(BOLD, 9f);
      stream.setNonStrokingColor(new Color(0x64, 0x74, 0x8B));
      stream.newLineAtOffset(MARGIN, y);
      stream.showText(label + ": ");
      stream.setFont(REGULAR, 9f);
      stream.setNonStrokingColor(Color.BLACK);
      stream.showText(sanitize(value));
      stream.endText();
      y -= LEADING;
    }

    void drawSectionHeading(String section) throws IOException {
      ensureSpace(LEADING * 3);
      y -= SECTION_GAP;

      stream.setNonStrokingColor(accent);
      stream.addRect(MARGIN, y + 2f, 3f, 12f);
      stream.fill();
      stream.setNonStrokingColor(Color.BLACK);

      stream.beginText();
      stream.setFont(BOLD, 12f);
      stream.setNonStrokingColor(accent);
      stream.newLineAtOffset(MARGIN + 10f, y + 4f);
      stream.showText(sanitize(section));
      stream.endText();
      stream.setNonStrokingColor(Color.BLACK);

      y -= LEADING + 6f;
    }

    void drawQuestion(int index, VantaQuestion question) throws IOException {
      ensureSpace(LEADING * 4);

      String number =
          question.getQuestionNumber() != null
              ? question.getQuestionNumber()
              : String.valueOf(index);

      drawText(number + ". " + sanitize(question.getText()), BOLD, BODY_SIZE, Color.BLACK, true);

      String answer =
          question.getFinalAnswer() != null && !question.getFinalAnswer().isBlank()
              ? question.getFinalAnswer()
              : question.getAiAnswer();

      if (answer == null || answer.isBlank()) {
        drawText("No answer provided.", ITALIC, BODY_SIZE, new Color(0x94, 0xA3, 0xB8));
      } else {
        drawWrapped(sanitize(answer), REGULAR, BODY_SIZE, new Color(0x1F, 0x29, 0x37));
      }

      if (question.getConfidenceScore() != null) {
        drawConfidenceNote(question.getConfidenceScore(), question.getStatus());
      }

      y -= 8f;
    }

    private void drawConfidenceNote(int score, VantaQuestion.QuestionStatus status)
        throws IOException {
      ensureSpace(LEADING);
      Color tone =
          score >= 90
              ? new Color(0x15, 0x80, 0x3D)
              : score >= 70 ? new Color(0xB4, 0x53, 0x09) : new Color(0xB9, 0x1C, 0x1C);

      stream.beginText();
      stream.setFont(REGULAR, 8f);
      stream.setNonStrokingColor(tone);
      stream.newLineAtOffset(MARGIN, y);
      stream.showText("Confidence " + score + "%  -  " + status.name().replace('_', ' '));
      stream.endText();
      stream.setNonStrokingColor(Color.BLACK);
      y -= LEADING;
    }

    private void drawDivider() throws IOException {
      ensureSpace(8f);
      stream.setStrokingColor(new Color(0xE2, 0xE8, 0xF0));
      stream.setLineWidth(0.75f);
      stream.moveTo(MARGIN, y);
      stream.lineTo(PAGE_SIZE.getWidth() - MARGIN, y);
      stream.stroke();
      stream.setStrokingColor(Color.BLACK);
      y -= 10f;
    }

    private void drawText(String text, PDFont font, float size, Color color) throws IOException {
      drawText(text, font, size, color, false);
    }

    private void drawText(String text, PDFont font, float size, Color color, boolean wrap)
        throws IOException {
      if (wrap) {
        drawWrapped(text, font, size, color);
        return;
      }
      ensureSpace(size + 4f);
      stream.beginText();
      stream.setFont(font, size);
      stream.setNonStrokingColor(color);
      stream.newLineAtOffset(MARGIN, y);
      stream.showText(text);
      stream.endText();
      stream.setNonStrokingColor(Color.BLACK);
      y -= size + 5f;
    }

    private void drawWrapped(String text, PDFont font, float size, Color color) throws IOException {
      for (String paragraph : text.split("\r?\n")) {
        if (paragraph.isBlank()) {
          y -= LEADING / 2f;
          continue;
        }
        for (String line : wrap(paragraph, font, size, contentWidth)) {
          ensureSpace(LEADING);
          stream.beginText();
          stream.setFont(font, size);
          stream.setNonStrokingColor(color);
          stream.newLineAtOffset(MARGIN, y);
          stream.showText(line);
          stream.endText();
          y -= LEADING;
        }
      }
      stream.setNonStrokingColor(Color.BLACK);
    }

    /** Greedy word wrap; falls back to hard-splitting tokens wider than the column. */
    private static List<String> wrap(String text, PDFont font, float size, float maxWidth)
        throws IOException {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();

      for (String word : text.split("\\s+")) {
        if (word.isEmpty()) continue;

        String candidate = line.length() == 0 ? word : line + " " + word;
        if (textWidth(font, size, candidate) <= maxWidth) {
          line.setLength(0);
          line.append(candidate);
          continue;
        }

        if (line.length() > 0) {
          lines.add(line.toString());
          line.setLength(0);
        }

        // A single token too wide for the column (a URL, a hash) must still fit.
        if (textWidth(font, size, word) > maxWidth) {
          StringBuilder chunk = new StringBuilder();
          for (char c : word.toCharArray()) {
            if (textWidth(font, size, chunk.toString() + c) > maxWidth) {
              lines.add(chunk.toString());
              chunk.setLength(0);
            }
            chunk.append(c);
          }
          line.append(chunk);
        } else {
          line.append(word);
        }
      }

      if (line.length() > 0) lines.add(line.toString());
      return lines;
    }

    private static float textWidth(PDFont font, float size, String text) throws IOException {
      return font.getStringWidth(text) / 1000f * size;
    }

    void finish() throws IOException {
      closeStream();
    }

    private void closeStream() throws IOException {
      if (stream != null) {
        stream.close();
        stream = null;
      }
    }
  }

  /**
   * PDType1Font only encodes WinAnsi. Smart quotes, em dashes and emoji from LLM output would
   * otherwise throw mid-render, so normalise them up front.
   */
  private static String sanitize(String text) {
    if (text == null) return "";
    String normalized =
        text.replace('‘', '\'')
            .replace('’', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace('–', '-')
            .replace('—', '-')
            .replace('…', '.')
            .replace(' ', ' ')
            .replace('\t', ' ');

    StringBuilder safe = new StringBuilder(normalized.length());
    for (char c : normalized.toCharArray()) {
      if (c == '\n' || c == '\r' || (c >= 32 && c <= 255)) {
        safe.append(c);
      } else {
        safe.append('?');
      }
    }
    return safe.toString();
  }
}
