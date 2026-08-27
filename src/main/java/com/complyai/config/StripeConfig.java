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
package com.complyai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

  @Value("${stripe.secret-key:}")
  String secretKey;

  @Value("${stripe.publishable-key:}")
  String publishableKey;

  @Value("${stripe.webhook-secret:}")
  String webhookSecret;

  @Value("${stripe.trial-days:14}")
  int trialDays;

  // Price ids are per-account and differ between test and live mode, so they
  // are configuration rather than constants in code.
  @Value("${stripe.price.starter:}")
  String starterPriceId;

  @Value("${stripe.price.professional:}")
  String professionalPriceId;

  @Value("${stripe.price.enterprise:}")
  String enterprisePriceId;

  @Value("${stripe.price.custom:}")
  String customPriceId;

  /** Where Stripe Checkout returns the customer. */
  @Value("${stripe.success-url:http://localhost:3000/dashboard/settings?billing=success}")
  String successUrl;

  @Value("${stripe.cancel-url:http://localhost:3000/dashboard/settings?billing=cancelled}")
  String cancelUrl;

  public String getSecretKey() {
    return secretKey;
  }

  public String getPublishableKey() {
    return publishableKey;
  }

  public String getWebhookSecret() {
    return webhookSecret;
  }

  public int getTrialDays() {
    return trialDays;
  }

  public String getStarterPriceId() {
    return starterPriceId;
  }

  public String getProfessionalPriceId() {
    return professionalPriceId;
  }

  public String getEnterprisePriceId() {
    return enterprisePriceId;
  }

  public String getCustomPriceId() {
    return customPriceId;
  }

  public String getSuccessUrl() {
    return successUrl;
  }

  public String getCancelUrl() {
    return cancelUrl;
  }

  public boolean hasWebhookSecret() {
    return webhookSecret != null && !webhookSecret.isBlank();
  }

  public boolean isConfigured() {
    return secretKey != null && !secretKey.isBlank();
  }
}
