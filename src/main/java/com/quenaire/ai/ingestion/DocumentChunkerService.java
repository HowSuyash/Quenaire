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

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentChunkerService {

  private final int chunkSize;
  private final int chunkOverlap;

  public DocumentChunkerService(
      @Value("${app.rag.chunk-size:1000}") int chunkSize,
      @Value("${app.rag.chunk-overlap:200}") int chunkOverlap) {
    this.chunkSize = chunkSize;
    this.chunkOverlap = chunkOverlap;
  }

  public List<DocumentChunk> chunk(String documentId, String content) {
    List<DocumentChunk> chunks = new ArrayList<>();
    if (content == null || content.isBlank()) {
      return chunks;
    }

    String[] words = content.split("\\s+");
    int totalWords = words.length;
    int start = 0;
    int chunkIndex = 0;

    while (start < totalWords) {
      int end = Math.min(start + chunkSize, totalWords);
      StringBuilder chunkText = new StringBuilder();
      for (int i = start; i < end; i++) {
        chunkText.append(words[i]);
        if (i < end - 1) {
          chunkText.append(" ");
        }
      }

      int startChar = calculateCharOffset(content, words, start);
      int endChar = calculateCharOffset(content, words, end);

      chunks.add(
          new DocumentChunk(
              documentId,
              chunkText.toString().trim(),
              chunkIndex++,
              end - start,
              startChar,
              endChar));

      if (end >= totalWords) {
        break;
      }
      start = end - chunkOverlap;
    }

    return chunks;
  }

  private int calculateCharOffset(String content, String[] words, int wordIndex) {
    if (wordIndex <= 0) return 0;
    if (wordIndex >= words.length) return content.length();

    int charCount = 0;
    for (int i = 0; i < wordIndex; i++) {
      int index = content.indexOf(words[i], charCount);
      if (index >= 0) {
        charCount = index + words[i].length();
      } else {
        charCount += words[i].length() + 1;
      }
    }
    return Math.min(charCount, content.length());
  }

  public record DocumentChunk(
      String documentId,
      String content,
      int chunkIndex,
      int tokenCount,
      int startChar,
      int endChar) {}
}
