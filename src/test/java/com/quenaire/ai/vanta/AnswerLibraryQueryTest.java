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
package com.quenaire.ai.vanta;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.quenaire.IntegrationTestBase;
import com.quenaire.ai.rag.VectorLiterals;
import com.quenaire.domain.questionnaire.vanta.AnswerLibraryRepository;
import com.quenaire.domain.questionnaire.vanta.QuestionType;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.tenant.TenantPlan;
import com.quenaire.domain.tenant.TenantRepository;
import com.quenaire.domain.tenant.TenantStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exercises the answer library's similarity query against a real Postgres.
 *
 * <p>{@code findSemanticMatches} catches any failure and falls back to word overlap, which means a
 * broken query produces a warning line and slightly worse answers rather than an error. It was
 * broken — Postgres could not infer a type for a parameter used only in {@code ? IS NULL} — and the
 * fallback hid that for as long as the feature had existed. These tests run the query itself, where
 * a failure has nowhere to hide.
 */
class AnswerLibraryQueryTest extends IntegrationTestBase {

  @Autowired AnswerLibraryRepository answerLibraryRepository;
  @Autowired TenantRepository tenantRepository;

  private UUID tenantId() {
    Tenant tenant = new Tenant();
    tenant.setName("Library Query");
    tenant.setSlug("library-query-" + System.nanoTime());
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setPlan(TenantPlan.FREE);
    return tenantRepository.save(tenant).getId();
  }

  @Test
  @DisplayName("the similarity query runs when filtering by question type")
  void runsWithQuestionType() {
    String vector = VectorLiterals.of(new float[VectorLiterals.DIMENSIONS]);

    assertThatCode(
            () ->
                answerLibraryRepository.findSemanticNeighbours(
                    tenantId(), vector, QuestionType.FREE_TEXT.name(), 5))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("the similarity query runs when no question type is given")
  void runsWithoutQuestionType() {
    String vector = VectorLiterals.of(new float[VectorLiterals.DIMENSIONS]);

    // The null branch is the one that failed: an untyped parameter Postgres would not accept.
    assertThatCode(
            () -> answerLibraryRepository.findSemanticNeighbours(tenantId(), vector, null, 5))
        .doesNotThrowAnyException();
  }
}
