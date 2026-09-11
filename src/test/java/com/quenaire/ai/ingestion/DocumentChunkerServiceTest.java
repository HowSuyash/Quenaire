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
package com.quenaire.ai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Chunking is pure string handling, so this test builds the service directly.
 *
 * <p>It previously extended the Testcontainers base, which meant asserting on how a paragraph is
 * split required booting Spring and a Postgres container.
 */
class DocumentChunkerServiceTest {

  private static final int CHUNK_SIZE = 1000;
  private static final int CHUNK_OVERLAP = 200;

  private DocumentChunkerService chunkerService;

  @BeforeEach
  void setUp() {
    chunkerService = new DocumentChunkerService(CHUNK_SIZE, CHUNK_OVERLAP);
  }

  @Test
  void shortTextStaysInOneChunk() {
    String text = "This is a short text that fits in one chunk.";

    List<DocumentChunkerService.DocumentChunk> chunks = chunkerService.chunk("doc-1", text);

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).content()).isEqualTo(text);
    assertThat(chunks.get(0).chunkIndex()).isZero();
  }

  @Test
  void longTextSplitsIntoOrderedChunks() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 2000; i++) {
      sb.append("word ").append(i).append(" ");
    }

    List<DocumentChunkerService.DocumentChunk> chunks =
        chunkerService.chunk("doc-1", sb.toString());

    assertThat(chunks).hasSizeGreaterThan(1);

    // Indices must be dense and ascending — retrieval reassembles by them.
    for (int i = 0; i < chunks.size(); i++) {
      assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
      assertThat(chunks.get(i).content()).isNotBlank();
    }
  }

  @Test
  void emptyContentProducesNoChunks() {
    assertThat(chunkerService.chunk("doc-1", "")).isEmpty();
  }

  @Test
  void chunksCarryTheirDocumentId() {
    List<DocumentChunkerService.DocumentChunk> chunks =
        chunkerService.chunk("doc-42", "Some content worth chunking.");

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.documentId()).isEqualTo("doc-42"));
  }
}
