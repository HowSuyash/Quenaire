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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quenaire.IntegrationTestBase;
import com.quenaire.domain.questionnaire.vanta.QuestionType;
import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionRepository;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.tenant.TenantPlan;
import com.quenaire.domain.tenant.TenantRepository;
import com.quenaire.domain.tenant.TenantStatus;
import com.quenaire.domain.user.User;
import com.quenaire.domain.user.UserRepository;
import com.quenaire.domain.user.UserRole;
import com.quenaire.domain.user.UserStatus;
import com.quenaire.security.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The audit trail behind an answer.
 *
 * <p>History was written into the question's own {@code metadata_json} as a JSON array — replacing
 * whatever that column held — and {@code getVersionHistory} returned an empty list without reading
 * any of it. So the review screen showed nothing, and recording history quietly destroyed other
 * data. Both halves are covered here.
 */
class VersionHistoryTest extends IntegrationTestBase {

  @Autowired VantaReviewService reviewService;
  @Autowired VantaQuestionRepository questionRepository;
  @Autowired VantaQuestionnaireRepository questionnaireRepository;
  @Autowired TenantRepository tenantRepository;
  @Autowired AnswerLibraryService answerLibraryService;
  @Autowired UserRepository userRepository;

  private Tenant tenant;
  private VantaQuestion question;
  private UUID reviewer;

  @BeforeEach
  void setUp() {
    tenant = new Tenant();
    tenant.setName("History");
    tenant.setSlug("history-" + System.nanoTime());
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setPlan(TenantPlan.FREE);
    tenant = tenantRepository.save(tenant);
    TenantContext.setCurrentTenant(tenant);

    // reviewed_by_id is a foreign key to users, so the reviewer has to exist.
    User user = new User();
    user.setEmail("reviewer-" + System.nanoTime() + "@example.test");
    user.setRole(UserRole.MEMBER);
    user.setStatus(UserStatus.ACTIVE);
    user.setTenant(tenant);
    reviewer = userRepository.save(user).getId();

    VantaQuestionnaire questionnaire = new VantaQuestionnaire();
    questionnaire.setTitle("Vendor Assessment");
    questionnaire.setSourceType(VantaQuestionnaire.SourceType.UPLOAD_EXCEL);
    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.READY_FOR_REVIEW);
    questionnaire.setTenant(tenant);
    questionnaire = questionnaireRepository.save(questionnaire);

    question = new VantaQuestion();
    question.setQuestionnaire(questionnaire);
    question.setQuestionNumber("D.2");
    question.setText("Is multi-factor authentication enforced?");
    question.setQuestionType(QuestionType.FREE_TEXT);
    question.setStatus(VantaQuestion.QuestionStatus.PENDING);
    question.setTenant(tenant);
    question.setFinalAnswer("Yes, on administrative accounts.");
    question.setMetadataJson("{\"importedFrom\":\"row 14\"}");
    question = questionRepository.save(question);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("an approval is recorded, with what the answer said before")
  void approvalIsRecorded() {
    UUID approver = reviewer;

    reviewService.approveQuestion(question.getId(), approver, "Checked against the policy");

    var history = reviewService.getVersionHistory(question.getId());

    assertThat(history).hasSize(1);
    assertThat(history.get(0).getAction()).isEqualTo("APPROVED");
    assertThat(history.get(0).getUserId()).isEqualTo(approver);
    assertThat(history.get(0).getPreviousStatus()).isEqualTo(VantaQuestion.QuestionStatus.PENDING);
    assertThat(history.get(0).getPreviousAnswer()).isEqualTo("Yes, on administrative accounts.");
    assertThat(history.get(0).getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("recording history leaves the question's own metadata alone")
  void metadataSurvives() {
    reviewService.approveQuestion(question.getId(), reviewer, null);

    VantaQuestion reloaded = questionRepository.findById(question.getId()).orElseThrow();

    // The old implementation replaced this column with a JSON array of history entries.
    assertThat(reloaded.getMetadataJson()).contains("importedFrom");
  }

  @Test
  @DisplayName("successive changes accumulate, newest first")
  void historyAccumulates() {
    reviewService.updateAnswer(
        question.getId(), reviewer, "Yes, on all accounts including remote access.", null);
    reviewService.approveQuestion(question.getId(), reviewer, null);

    var history = reviewService.getVersionHistory(question.getId());

    assertThat(history).hasSize(2);
    assertThat(history).extracting(h -> h.getAction()).containsExactly("APPROVED", "HUMAN_EDITED");
    // The approval records the edited answer as what it replaced.
    assertThat(history.get(0).getPreviousAnswer())
        .isEqualTo("Yes, on all accounts including remote access.");
  }

  @Test
  @DisplayName("a question with no changes has no history rather than failing")
  void emptyHistory() {
    assertThat(reviewService.getVersionHistory(question.getId())).isEmpty();
  }

  @Test
  @DisplayName("approving a corrected answer keeps the correction")
  void approvalKeepsTheReviewersAnswer() {
    // The model got it wrong; a reviewer fixed it; approval must not undo that.
    question.setAiAnswer("No, multi-factor authentication is not enforced.");
    questionRepository.save(question);

    reviewService.updateAnswer(
        question.getId(), reviewer, "Yes, enforced on every administrative account.", null);
    VantaQuestion approved = reviewService.approveQuestion(question.getId(), reviewer, null);

    // setFinalAnswer(getAiAnswer()) on approval restored the model's wrong answer,
    // and the final answer is the text exported and sent to the vendor.
    assertThat(approved.getFinalAnswer())
        .isEqualTo("Yes, enforced on every administrative account.");
  }

  @Test
  @DisplayName("a question with no answer at all cannot be approved")
  void cannotApproveAnEmptyAnswer() {
    question.setFinalAnswer(null);
    question.setAiAnswer(null);
    question.setHumanAnswer(null);
    questionRepository.save(question);

    assertThatThrownBy(() -> reviewService.approveQuestion(question.getId(), reviewer, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no answer");
  }

  @Test
  @DisplayName("the answer library learns the approved answer, not the model's")
  void libraryLearnsTheApprovedAnswer() {
    question.setAiAnswer("No, multi-factor authentication is not enforced.");
    questionRepository.save(question);

    reviewService.updateAnswer(
        question.getId(), reviewer, "Yes, enforced on every administrative account.", null);
    reviewService.approveQuestion(question.getId(), reviewer, null);

    // The library is what gets reused on later questionnaires. Storing the model's
    // original meant a reviewer's correction was discarded and the wrong answer was
    // served again every time the same question came up.
    var entry = answerLibraryService.findExactMatch(question.getText(), question.getQuestionType());

    assertThat(entry).isPresent();
    assertThat(entry.get().getApprovedAnswer())
        .isEqualTo("Yes, enforced on every administrative account.");
  }
}
