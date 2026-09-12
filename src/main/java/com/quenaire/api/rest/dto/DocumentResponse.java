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
package com.quenaire.api.rest.dto;

import com.quenaire.domain.document.Document;
import com.quenaire.domain.document.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * What a client is told about an uploaded document.
 *
 * <p>Named fields rather than the entity, for the same reason as the questionnaire responses: the
 * entity exposes three lazy associations -- {@code tenant}, {@code createdByUser} and the whole
 * {@code chunks} collection -- and Jackson follows them. Serialising a document therefore either
 * failed outside a transaction or returned every chunk of the customer's security documentation,
 * each with its embedding, in a response meant to confirm an upload.
 *
 * <p>{@code storagePath} is left out deliberately. Where a file sits on the server's disk is not
 * something a client needs, and naming it is free reconnaissance.
 */
public record DocumentResponse(
    UUID id,
    String title,
    String description,
    String sourceType,
    String sourceUrl,
    String fileName,
    String mimeType,
    Long fileSize,
    DocumentStatus status,
    Integer pageCount,
    Integer wordCount,
    String language,
    String processingError,
    Instant processedAt,
    UUID createdById,
    Instant createdAt,
    Instant updatedAt) {

  public static DocumentResponse of(Document d) {
    return new DocumentResponse(
        d.getId(),
        d.getTitle(),
        d.getDescription(),
        d.getSourceType(),
        d.getSourceUrl(),
        d.getFileName(),
        d.getMimeType(),
        d.getFileSize(),
        d.getStatus(),
        d.getPageCount(),
        d.getWordCount(),
        d.getLanguage(),
        d.getProcessingError(),
        d.getProcessedAt(),
        d.getCreatedById(),
        d.getCreatedAt(),
        d.getUpdatedAt());
  }
}
