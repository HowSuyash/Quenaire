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

import org.junit.jupiter.api.Test;

class DocumentParserServiceTest {
  // Pure parsing: no Spring context, no database.
  private final DocumentParserService parserService = new DocumentParserService();

  @Test
  void parseText() {
    String content = "This is a test document content. It has multiple sentences. And words.";
    var result = parserService.parseText(content, "text/plain");

    assertThat(result.content()).isEqualTo(content);
    assertThat(result.mimeType()).isEqualTo("text/plain");
    assertThat(result.wordCount()).isGreaterThan(0);
  }

  @Test
  void parseEmptyText() {
    var result = parserService.parseText("", "text/plain");
    assertThat(result.content()).isEmpty();
    assertThat(result.wordCount()).isZero();
  }
}
