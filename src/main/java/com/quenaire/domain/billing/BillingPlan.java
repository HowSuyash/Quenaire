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
package com.quenaire.domain.billing;

public enum BillingPlan {
  FREE("Free", 0, 0, 5, 100, 1000, false, false, false),
  STARTER("Starter", 49, 4900, 20, 500, 5000, true, false, false),
  PROFESSIONAL("Professional", 199, 19900, 100, 2000, 25000, true, true, false),
  ENTERPRISE("Enterprise", 599, 59900, 500, 10000, 100000, true, true, true),
  CUSTOM("Custom", 0, 0, -1, -1, -1, true, true, true);

  private final String name;
  private final int monthlyPriceUsd;
  private final int annualPriceUsd;
  private final int maxQuestionnairesPerMonth;
  private final int maxDocuments;
  private final int maxQuestionsPerQuestionnaire;
  private final boolean apiAccess;
  private final boolean integrationsEnabled;
  private final boolean ssoEnabled;

  BillingPlan(
      String name,
      int monthlyPriceUsd,
      int annualPriceUsd,
      int maxQuestionnairesPerMonth,
      int maxDocuments,
      int maxQuestionsPerQuestionnaire,
      boolean apiAccess,
      boolean integrationsEnabled,
      boolean ssoEnabled) {
    this.name = name;
    this.monthlyPriceUsd = monthlyPriceUsd;
    this.annualPriceUsd = annualPriceUsd;
    this.maxQuestionnairesPerMonth = maxQuestionnairesPerMonth;
    this.maxDocuments = maxDocuments;
    this.maxQuestionsPerQuestionnaire = maxQuestionsPerQuestionnaire;
    this.apiAccess = apiAccess;
    this.integrationsEnabled = integrationsEnabled;
    this.ssoEnabled = ssoEnabled;
  }

  public String getName() {
    return name;
  }

  public int getMonthlyPriceUsd() {
    return monthlyPriceUsd;
  }

  public int getAnnualPriceUsd() {
    return annualPriceUsd;
  }

  public int getMaxQuestionnairesPerMonth() {
    return maxQuestionnairesPerMonth;
  }

  public int getMaxDocuments() {
    return maxDocuments;
  }

  public int getMaxQuestionsPerQuestionnaire() {
    return maxQuestionsPerQuestionnaire;
  }

  public boolean isApiAccess() {
    return apiAccess;
  }

  public boolean isIntegrationsEnabled() {
    return integrationsEnabled;
  }

  public boolean isSsoEnabled() {
    return ssoEnabled;
  }

  public boolean isUnlimited(String feature) {
    switch (feature) {
      case "questionnaires":
        return maxQuestionnairesPerMonth == -1;
      case "documents":
        return maxDocuments == -1;
      case "questions":
        return maxQuestionsPerQuestionnaire == -1;
      default:
        return false;
    }
  }
}
