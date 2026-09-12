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
package com.quenaire.api.rest.vanta.dto;

import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What a client is told about a questionnaire.
 *
 * <p>The controllers returned the JPA entity itself, which has two consequences. Jackson emits
 * every readable property, and {@code originalFileBytes} is one of them -- so the customer's entire
 * uploaded workbook travelled back as base64 in a response whose purpose was to list titles. And
 * Jackson walks the entity's associations, which are lazy, so the listing answered 500 as soon as a
 * questionnaire existed: "failed to lazily initialize a collection of role
 * VantaQuestionnaire.questions".
 *
 * <p>An explicit record is the boundary. A field reaches a client because it is named here, not
 * because nobody remembered to annotate it away.
 */
public record QuestionnaireResponse(
    UUID id,
    String title,
    String description,
    VantaQuestionnaire.SourceType sourceType,
    String sourceUrl,
    /** The name the customer uploaded. Not its contents -- see the note above. */
    String fileName,
    VantaQuestionnaire.QuestionnaireStatus status,
    Integer totalQuestions,
    Integer answeredQuestions,
    Integer approvedQuestions,
    Integer highConfidenceQuestions,
    Integer needsReviewQuestions,
    OffsetDateTime dueDate,
    OffsetDateTime submittedAt,
    OffsetDateTime completedAt,
    UUID tenantId,
    Instant createdAt,
    Instant updatedAt) {

  public static QuestionnaireResponse of(VantaQuestionnaire q) {
    return new QuestionnaireResponse(
        q.getId(),
        q.getTitle(),
        q.getDescription(),
        q.getSourceType(),
        q.getSourceUrl(),
        q.getFileName(),
        q.getStatus(),
        q.getTotalQuestions(),
        q.getAnsweredQuestions(),
        q.getApprovedQuestions(),
        q.getHighConfidenceQuestions(),
        q.getNeedsReviewQuestions(),
        q.getDueDate(),
        q.getSubmittedAt(),
        q.getCompletedAt(),
        q.getTenantId(),
        q.getCreatedAt(),
        q.getUpdatedAt());
  }
}
