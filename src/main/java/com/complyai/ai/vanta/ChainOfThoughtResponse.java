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

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The sections of a chain-of-thought completion, separated from one another.
 *
 * <p>The prompt asks the model for a labelled response: its reasoning, then the answer, then a
 * self-assessed confidence and anything it could not find. All of that used to be stored as the
 * answer and carried straight into the PDF a customer sends to a vendor — headers, reasoning and
 * all. The answer a customer sends is the {@code answer} section alone; the rest is working notes.
 */
public record ChainOfThoughtResponse(
    String answer,
    String thinking,
    String selectedOption,
    StatedConfidence statedConfidence,
    String missingInfo) {

  /** The model's own view of how well the context answered the question. */
  public enum StatedConfidence {
    HIGH,
    MEDIUM,
    LOW,
    /** The model did not say, or said something unrecognisable. */
    UNSTATED
  }

  /** Section labels the prompt asks for. Order here does not matter; position in the text does. */
  private static final List<String> SECTIONS =
      List.of("THINKING:", "ANSWER:", "SELECTED_OPTION:", "CONFIDENCE:", "MISSING_INFO:");

  private static final Pattern CONFIDENCE_VALUE =
      Pattern.compile("\\b(HIGH|MEDIUM|LOW)\\b", Pattern.CASE_INSENSITIVE);

  /** Values models offer for "nothing is missing"; treated as absent rather than as content. */
  private static final List<String> NOTHING_MISSING = List.of("none", "n/a", "na", "nothing", "-");

  public static ChainOfThoughtResponse parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return new ChainOfThoughtResponse("", null, null, StatedConfidence.UNSTATED, null);
    }

    String answer = section(raw, "ANSWER:");
    if (answer == null) {
      // The model ignored the format. Everything before the first trailing label is the
      // best available answer -- returning nothing would discard a usable response.
      answer = trimTrailingSections(raw);
    }

    return new ChainOfThoughtResponse(
        answer.trim(),
        blankToNull(section(raw, "THINKING:")),
        blankToNull(section(raw, "SELECTED_OPTION:")),
        confidence(section(raw, "CONFIDENCE:")),
        missingInfo(section(raw, "MISSING_INFO:")));
  }

  /** Text between {@code label} and whichever other section label comes next. */
  private static String section(String raw, String label) {
    int start = indexOfLabel(raw, label);
    if (start < 0) {
      return null;
    }
    int from = start + label.length();
    int end = raw.length();
    for (String other : SECTIONS) {
      if (other.equals(label)) {
        continue;
      }
      int next = indexOfLabel(raw, other, from);
      if (next >= 0 && next < end) {
        end = next;
      }
    }
    return raw.substring(from, end).trim();
  }

  private static int indexOfLabel(String raw, String label) {
    return indexOfLabel(raw, label, 0);
  }

  private static int indexOfLabel(String raw, String label, int from) {
    return raw.toUpperCase(Locale.ROOT).indexOf(label, from);
  }

  /** Drops any labelled block from the end, for responses that never marked an ANSWER. */
  private static String trimTrailingSections(String raw) {
    int cut = raw.length();
    for (String label : SECTIONS) {
      int at = indexOfLabel(raw, label);
      if (at >= 0 && at < cut) {
        cut = at;
      }
    }
    return cut > 0 ? raw.substring(0, cut) : raw;
  }

  private static StatedConfidence confidence(String value) {
    if (value == null) {
      return StatedConfidence.UNSTATED;
    }
    Matcher m = CONFIDENCE_VALUE.matcher(value);
    return m.find()
        ? StatedConfidence.valueOf(m.group(1).toUpperCase(Locale.ROOT))
        : StatedConfidence.UNSTATED;
  }

  private static String missingInfo(String value) {
    String first = blankToNull(value);
    if (first == null) {
      return null;
    }
    // Models append prose after this field; only the first line is the field itself.
    String line = first.lines().findFirst().orElse("").trim();
    String bare = line.replaceAll("[.\\s]+$", "").toLowerCase(Locale.ROOT);
    return NOTHING_MISSING.contains(bare) || bare.isEmpty() ? null : line;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public Optional<String> missing() {
    return Optional.ofNullable(missingInfo);
  }
}
