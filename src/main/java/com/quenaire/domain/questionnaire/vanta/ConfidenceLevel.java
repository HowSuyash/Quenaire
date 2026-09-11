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
package com.quenaire.domain.questionnaire.vanta;

public enum ConfidenceLevel {
  HIGH(90, 100, "High", "🟢", "Auto-approve ready"),
  MEDIUM(70, 89, "Medium", "🟡", "Human review recommended"),
  LOW(0, 69, "Low", "🔴", "Requires SecOps/Legal review");

  private final int minScore;
  private final int maxScore;
  private final String label;
  private final String emoji;
  private final String description;

  ConfidenceLevel(int minScore, int maxScore, String label, String emoji, String description) {
    this.minScore = minScore;
    this.maxScore = maxScore;
    this.label = label;
    this.emoji = emoji;
    this.description = description;
  }

  public static ConfidenceLevel fromScore(int score) {
    for (ConfidenceLevel level : values()) {
      if (score >= level.minScore && score <= level.maxScore) {
        return level;
      }
    }
    return LOW;
  }

  public int getMinScore() {
    return minScore;
  }

  public int getMaxScore() {
    return maxScore;
  }

  public String getLabel() {
    return label;
  }

  public String getEmoji() {
    return emoji;
  }

  public String getDescription() {
    return description;
  }
}
