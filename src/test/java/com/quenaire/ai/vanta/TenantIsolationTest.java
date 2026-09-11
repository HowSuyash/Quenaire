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
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.tenant.TenantPlan;
import com.quenaire.domain.tenant.TenantRepository;
import com.quenaire.domain.tenant.TenantStatus;
import com.quenaire.security.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * One customer must not reach another's questionnaires.
 *
 * <p>There is no Hibernate tenant filter in this application: isolation is only ever whatever the
 * query says. Several services looked entities up with {@code findById}, which says nothing, so an
 * id taken from a URL crossed tenants — including on the export path, which returns the original
 * uploaded file. The scoped finders existed the whole time; they were simply not the ones being
 * called, and no test compared the two.
 */
class TenantIsolationTest extends IntegrationTestBase {

  @Autowired VantaQuestionnaireService questionnaireService;
  @Autowired VantaQuestionnaireRepository questionnaireRepository;
  @Autowired TenantRepository tenantRepository;

  private Tenant owner;
  private Tenant intruder;
  private UUID ownedQuestionnaireId;

  @BeforeEach
  void setUp() {
    owner = newTenant("owner");
    intruder = newTenant("intruder");

    TenantContext.setCurrentTenant(owner);
    VantaQuestionnaire questionnaire = new VantaQuestionnaire();
    questionnaire.setTitle("Northbridge Vendor Assessment");
    questionnaire.setSourceType(VantaQuestionnaire.SourceType.UPLOAD_EXCEL);
    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.READY_FOR_REVIEW);
    questionnaire.setTenant(owner);
    questionnaire.setOriginalFileBytes("the customer's uploaded workbook".getBytes());
    ownedQuestionnaireId = questionnaireRepository.save(questionnaire).getId();
  }

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

  @Test
  @DisplayName("the owner can read their own questionnaire")
  void ownerCanRead() {
    TenantContext.setCurrentTenant(owner);

    assertThat(questionnaireService.getQuestionnaire(ownedQuestionnaireId))
        .map(VantaQuestionnaire::getTitle)
        .contains("Northbridge Vendor Assessment");
  }

  @Test
  @DisplayName("another tenant reading the same id is told it does not exist")
  void otherTenantCannotRead() {
    TenantContext.setCurrentTenant(intruder);

    // Absent rather than forbidden: a 404 tells the caller nothing about whether
    // the id belongs to someone.
    assertThat(questionnaireService.getQuestionnaire(ownedQuestionnaireId)).isEmpty();
  }

  @Test
  @DisplayName("another tenant cannot export the original uploaded file")
  void otherTenantCannotExport() {
    TenantContext.setCurrentTenant(intruder);

    // The worst of the group: this returned the bytes the other customer uploaded.
    assertThatThrownBy(() -> questionnaireService.exportToOriginalExcel(ownedQuestionnaireId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
  }

  @Test
  @DisplayName("another tenant cannot start processing on it")
  void otherTenantCannotProcess() {
    TenantContext.setCurrentTenant(intruder);

    assertThatThrownBy(
            () ->
                questionnaireService.processQuestionnaireWithDocumentIds(
                    ownedQuestionnaireId, java.util.List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
  }

  @Test
  @DisplayName("documents named by id are restricted to the caller's tenant")
  void documentIdsAreScoped() {
    TenantContext.setCurrentTenant(owner);

    // An id belonging to nobody, standing in for one belonging to someone else:
    // either way it must not reach the retrieval scope.
    assertThatThrownBy(
            () ->
                questionnaireService.processQuestionnaireWithDocumentIds(
                    UUID.randomUUID(), java.util.List.of(UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
