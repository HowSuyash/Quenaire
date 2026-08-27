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
package com.complyai.integration.loopio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Loopio OAuth2 client-credentials configuration.
 *
 * <p>Loopio issues a client id/secret per workspace; tokens are short-lived and fetched on demand
 * by {@link LoopioIntegrationService}.
 */
@Configuration
public class LoopioConfig {

  @Value("${loopio.client-id:}")
  private String clientId;

  @Value("${loopio.client-secret:}")
  private String clientSecret;

  @Value("${loopio.base-url:https://api.loopio.com}")
  private String baseUrl;

  @Value("${loopio.auth-url:https://api.loopio.com/oauth2/access_token}")
  private String authUrl;

  /** Loopio library stack that answers are written back into. */
  @Value("${loopio.default-stack-id:}")
  private String defaultStackId;

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getAuthUrl() {
    return authUrl;
  }

  public String getDefaultStackId() {
    return defaultStackId;
  }

  public boolean isConfigured() {
    return clientId != null
        && !clientId.isBlank()
        && clientSecret != null
        && !clientSecret.isBlank();
  }
}
