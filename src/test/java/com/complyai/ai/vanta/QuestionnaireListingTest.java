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

import com.complyai.IntegrationTestBase;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaire;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.tenant.TenantPlan;
import com.complyai.domain.tenant.TenantRepository;
import com.complyai.domain.tenant.TenantStatus;
import com.complyai.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

/**
 * The questionnaire listing.
 *
 * <p>The controller returned {@code List.of()} unconditionally with a TODO where the query
 * belonged, so the product's main screen was empty however many questionnaires a customer had
 * uploaded — and it answered 200, so it looked like an empty account rather than a fault. Nothing
 * asserted otherwise.
 */
class QuestionnaireListingTest extends IntegrationTestBase {

  @Autowired VantaQuestionnaireService questionnaireService;
  @Autowired VantaQuestionnaireRepository questionnaireRepository;
  @Autowired TenantRepository tenantRepository;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private Tenant newTenant(String slug) {
    Tenant tenant = new Tenant();
    tenant.setName(slug);
    tenant.setSlug(slug + "-" + System.nanoTime());
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setPlan(TenantPlan.FREE);
    return tenantRepository.save(tenant);
  }

  private void createQuestionnaire(Tenant tenant, String title) {
    VantaQuestionnaire questionnaire = new VantaQuestionnaire();
    questionnaire.setTitle(title);
    questionnaire.setSourceType(VantaQuestionnaire.SourceType.UPLOAD_PDF);
    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.PROCESSING);
    questionnaire.setTenant(tenant);
    questionnaireRepository.save(questionnaire);
  }

  @Test
  @DisplayName("uploaded questionnaires appear in the listing")
  void listsWhatWasUploaded() {
    Tenant tenant = newTenant("listing");
    TenantContext.setCurrentTenant(tenant);

    createQuestionnaire(tenant, "Northbridge Vendor Assessment");
    createQuestionnaire(tenant, "Acme Security Review");

    Page<VantaQuestionnaire> page = questionnaireService.listQuestionnaires(0, 20);

    assertThat(page.getContent()).hasSize(2);
    assertThat(page.getContent())
        .extracting(VantaQuestionnaire::getTitle)
        .containsExactlyInAnyOrder("Northbridge Vendor Assessment", "Acme Security Review");
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  @DisplayName("one tenant cannot see another's questionnaires")
  void listingIsScopedToTheTenant() {
    Tenant mine = newTenant("mine");
    Tenant theirs = newTenant("theirs");

    TenantContext.setCurrentTenant(theirs);
    createQuestionnaire(theirs, "Their Confidential Assessment");

    TenantContext.setCurrentTenant(mine);
    createQuestionnaire(mine, "My Assessment");

    Page<VantaQuestionnaire> page = questionnaireService.listQuestionnaires(0, 20);

    assertThat(page.getContent())
        .extracting(VantaQuestionnaire::getTitle)
        .containsExactly("My Assessment");
  }

  @Test
  @DisplayName("a caller cannot ask for an unbounded page")
  void pageSizeIsBounded() {
    Tenant tenant = newTenant("bounded");
    TenantContext.setCurrentTenant(tenant);

    // Left unbounded this is a request for every row the tenant has, at the client's choosing.
    assertThat(questionnaireService.listQuestionnaires(0, 10_000).getSize()).isEqualTo(100);
    // And a nonsensical size should not reach the database as one.
    assertThat(questionnaireService.listQuestionnaires(0, 0).getSize()).isEqualTo(1);
    assertThat(questionnaireService.listQuestionnaires(-5, 20).getNumber()).isZero();
  }
}
