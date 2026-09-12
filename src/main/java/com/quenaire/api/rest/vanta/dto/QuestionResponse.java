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

import com.quenaire.domain.questionnaire.vanta.QuestionType;
import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What a client is told about a question.
 *
 * <p>Named fields rather than the entity. The entity carries three associations Jackson would
 * follow -- {@code questionnaire}, {@code tenant} and {@code assignedTo} -- all lazy, so
 * serialising it either fails outside a transaction or drags a second entity's contents into a
 * response about a question.
 *
 * <p>The answer fields are all here on purpose. A reviewer needs to see what the model said, what a
 * human changed it to, and which of those is going out, or the review screen cannot do its job.
 */
public record QuestionResponse(
    UUID id,
    UUID questionnaireId,
    String questionNumber,
    String text,
    String originalText,
    String category,
    String subCategory,
    String section,
    QuestionType questionType,
    String optionsJson,
    Boolean isRequired,
    Boolean isAttachmentRequest,
    VantaQuestion.QuestionStatus status,
    Integer confidenceScore,
    String aiAnswer,
    String humanAnswer,
    String finalAnswer,
    String selectedOption,
    String reviewNotes,
    Boolean stalenessDetected,
    List<UUID> citationIds,
    UUID assignedToId,
    UUID reviewedById,
    OffsetDateTime reviewedAt,
    UUID approvedById,
    OffsetDateTime approvedAt,
    Integer rowIndex,
    String sheetName,
    UUID tenantId) {

  public static QuestionResponse of(VantaQuestion q) {
    return new QuestionResponse(
        q.getId(),
        q.getQuestionnaireId(),
        q.getQuestionNumber(),
        q.getText(),
        q.getOriginalText(),
        q.getCategory(),
        q.getSubCategory(),
        q.getSection(),
        q.getQuestionType(),
        q.getOptionsJson(),
        q.getIsRequired(),
        q.getIsAttachmentRequest(),
        q.getStatus(),
        q.getConfidenceScore(),
        q.getAiAnswer(),
        q.getHumanAnswer(),
        q.getFinalAnswer(),
        q.getSelectedOption(),
        q.getReviewNotes(),
        q.getStalenessDetected(),
        q.getCitationIds(),
        q.getAssignedToId(),
        q.getReviewedById(),
        q.getReviewedAt(),
        q.getApprovedById(),
        q.getApprovedAt(),
        q.getRowIndex(),
        q.getSheetName(),
        q.getTenantId());
  }
}
