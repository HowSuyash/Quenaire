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
package com.complyai.domain.questionnaire.vanta;

public enum QuestionType {
  FREE_TEXT("Free Text", "free_text"),
  SINGLE_CHOICE("Single Choice", "single_choice"),
  MULTIPLE_CHOICE("Multiple Choice", "multiple_choice"),
  YES_NO("Yes/No", "yes_no"),
  YES_NO_NA("Yes/No/N/A", "yes_no_na"),
  YES_NO_PARTIAL("Yes/No/Partial", "yes_no_partial"),
  NUMERIC("Numeric", "numeric"),
  DATE("Date", "date"),
  FILE_UPLOAD("File Upload", "file_upload"),
  TABLE("Table/Grid", "table"),
  ATTACHMENT("Attachment Request", "attachment");

  private final String displayName;
  private final String code;

  QuestionType(String displayName, String code) {
    this.displayName = displayName;
    this.code = code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getCode() {
    return code;
  }

  public static QuestionType fromCode(String code) {
    for (QuestionType type : values()) {
      if (type.code.equalsIgnoreCase(code)) return type;
    }
    return FREE_TEXT;
  }

  public boolean hasOptions() {
    return this == SINGLE_CHOICE
        || this == MULTIPLE_CHOICE
        || this == YES_NO
        || this == YES_NO_NA
        || this == YES_NO_PARTIAL;
  }

  public String[] getDefaultOptions() {
    return switch (this) {
      case YES_NO -> new String[] {"Yes", "No"};
      case YES_NO_NA -> new String[] {"Yes", "No", "N/A"};
      case YES_NO_PARTIAL -> new String[] {"Yes", "No", "Partial"};
      default -> new String[] {};
    };
  }
}
