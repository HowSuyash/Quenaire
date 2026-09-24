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

import static org.assertj.core.api.Assertions.assertThat;

import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.tenant.Tenant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * What may leave the application in a response body.
 *
 * <p>The controllers used to return the JPA entities themselves. Jackson emits every readable
 * property of whatever it is handed, so a request for a list of questionnaire titles came back with
 * {@code originalFileBytes} — the customer's entire uploaded workbook, base64 encoded — and with
 * the tenant row attached. Nothing asserted otherwise, and the field had no annotation keeping it
 * out.
 *
 * <p>These tests are about the shape of the response, not the database, so they need no
 * infrastructure and run in the fast suite.
 */
class ResponseBoundaryTest {

  private static final String CONFIDENTIAL = "the customer's confidential workbook";

  private final ObjectMapper mapper = new ObjectMapper();

  private VantaQuestionnaire questionnaireHoldingAnUpload() {
    Tenant tenant = new Tenant();
    tenant.setName("Acme Security");
    tenant.setSlug("acme-security");

    VantaQuestionnaire questionnaire = new VantaQuestionnaire();
    questionnaire.setTitle("Northbridge VRM-2026-0891");
    questionnaire.setSourceType(VantaQuestionnaire.SourceType.UPLOAD_EXCEL);
    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.READY_FOR_REVIEW);
    questionnaire.setTenant(tenant);
    questionnaire.setOriginalFileBytes(CONFIDENTIAL.getBytes(StandardCharsets.UTF_8));
    return questionnaire;
  }

  @Test
  @DisplayName("a questionnaire response carries no part of the uploaded file")
  void uploadedFileNeverLeaves() throws Exception {
    String json =
        mapper.writeValueAsString(QuestionnaireResponse.of(questionnaireHoldingAnUpload()));

    assertThat(json).doesNotContain("originalFileBytes");
    assertThat(json).doesNotContain(CONFIDENTIAL);
    // Base64 is how it would appear if it were serialised, so check for that too.
    assertThat(json)
        .doesNotContain(
            Base64.getEncoder().encodeToString(CONFIDENTIAL.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName("the entity itself would have leaked it, which is why the response type exists")
  void theEntityIsNotSafeToReturn() throws Exception {
    VantaQuestionnaire entity = questionnaireHoldingAnUpload();

    // Not a recommendation — a record of why controllers must not return this. If this ever
    // stops being true the comment above should change, not this assertion be deleted.
    String json = mapper.writeValueAsString(entity);

    assertThat(json).contains("originalFileBytes");
  }

  @Test
  @DisplayName("a questionnaire response carries no tenant row and no question list")
  void noAssociationsRideAlong() throws Exception {
    String json =
        mapper.writeValueAsString(QuestionnaireResponse.of(questionnaireHoldingAnUpload()));

    // Serialising these is what made the listing answer 500: both are lazy, and Jackson runs
    // after the transaction has closed. tenantId is the identifier and is fine.
    assertThat(json).doesNotContain("\"tenant\"");
    assertThat(json).doesNotContain("\"questions\"");
    assertThat(json).contains("tenantId");
  }

  @Test
  @DisplayName("a question response keeps the answer fields a reviewer needs")
  void questionKeepsWhatTheReviewScreenUses() throws Exception {
    VantaQuestion question = new VantaQuestion();
    question.setText("Is data encrypted at rest?");
    question.setAiAnswer("AES-256 under provider-managed keys.");
    question.setHumanAnswer("AES-256, keys rotated every twelve months.");
    question.setFinalAnswer("AES-256, keys rotated every twelve months.");
    question.setStatus(VantaQuestion.QuestionStatus.APPROVED);

    String json = mapper.writeValueAsString(QuestionResponse.of(question));

    assertThat(json).contains("aiAnswer", "humanAnswer", "finalAnswer");
    // But not the way back to the questionnaire or the tenant.
    assertThat(json).doesNotContain("\"questionnaire\"", "\"tenant\"", "\"assignedTo\"");
  }
}
