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

import static org.assertj.core.api.Assertions.assertThat;

import com.complyai.ai.vanta.ChainOfThoughtResponse.StatedConfidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The answer a customer sends to their vendor is whatever this class returns, so the cases below
 * decide whether internal scaffolding reaches an outside reader.
 */
class ChainOfThoughtResponseTest {

  /** Verbatim shape of a real llama3.2 completion, including the prose it adds afterwards. */
  private static final String REAL_RESPONSE =
      """
      THINKING:
      The context states that disks are encrypted. I should quote it.

      ANSWER:
      Data at rest is encrypted using AES-256 under keys held in the provider's KMS.
      The policy states: "Information held on disk is protected with AES-256 encryption".

      CONFIDENCE: HIGH
      MISSING_INFO: None

      Rationale: The provided context explicitly states the algorithm.
      """;

  @Test
  @DisplayName("the answer is the answer section, without headers or reasoning")
  void extractsOnlyTheAnswer() {
    ChainOfThoughtResponse parsed = ChainOfThoughtResponse.parse(REAL_RESPONSE);

    assertThat(parsed.answer()).startsWith("Data at rest is encrypted using AES-256");
    assertThat(parsed.answer())
        .as("scaffolding must not reach the customer's PDF")
        .doesNotContain("THINKING:", "ANSWER:", "CONFIDENCE:", "MISSING_INFO:", "Rationale:");
  }

  @Test
  @DisplayName("the model's own confidence is read back")
  void readsStatedConfidence() {
    assertThat(ChainOfThoughtResponse.parse(REAL_RESPONSE).statedConfidence())
        .isEqualTo(StatedConfidence.HIGH);
    assertThat(ChainOfThoughtResponse.parse("ANSWER: x\nCONFIDENCE: low").statedConfidence())
        .isEqualTo(StatedConfidence.LOW);
    assertThat(ChainOfThoughtResponse.parse("ANSWER: x").statedConfidence())
        .isEqualTo(StatedConfidence.UNSTATED);
  }

  @Test
  @DisplayName("'None' is not a gap")
  void treatsNoneAsNothingMissing() {
    assertThat(ChainOfThoughtResponse.parse(REAL_RESPONSE).missing()).isEmpty();
    assertThat(ChainOfThoughtResponse.parse("ANSWER: x\nMISSING_INFO: N/A.").missing()).isEmpty();
    assertThat(
            ChainOfThoughtResponse.parse("ANSWER: x\nMISSING_INFO: The retention period").missing())
        .contains("The retention period");
  }

  @Test
  @DisplayName("a response that ignores the format is still usable")
  void fallsBackToTheWholeResponse() {
    ChainOfThoughtResponse parsed =
        ChainOfThoughtResponse.parse("Yes, all disks are encrypted with AES-256.");

    assertThat(parsed.answer()).isEqualTo("Yes, all disks are encrypted with AES-256.");
    assertThat(parsed.thinking()).isNull();
  }

  @Test
  @DisplayName("trailing labels are dropped from an unlabelled answer")
  void trimsTrailingLabelsWhenAnswerIsUnlabelled() {
    ChainOfThoughtResponse parsed =
        ChainOfThoughtResponse.parse("Yes, disks are encrypted.\n\nCONFIDENCE: MEDIUM");

    assertThat(parsed.answer()).isEqualTo("Yes, disks are encrypted.");
    assertThat(parsed.statedConfidence()).isEqualTo(StatedConfidence.MEDIUM);
  }

  @Test
  @DisplayName("an empty completion yields an empty answer rather than an exception")
  void handlesEmptyResponse() {
    assertThat(ChainOfThoughtResponse.parse(null).answer()).isEmpty();
    assertThat(ChainOfThoughtResponse.parse("   ").answer()).isEmpty();
  }

  @Test
  @DisplayName("a named option is read back")
  void readsSelectedOption() {
    assertThat(
            ChainOfThoughtResponse.parse("ANSWER: We do.\nSELECTED_OPTION: Yes\nCONFIDENCE: HIGH")
                .selectedOption())
        .isEqualTo("Yes");
  }
}
