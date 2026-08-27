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
package com.complyai.integration.confluence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfluenceConfig {

  @Value("${confluence.base-url:}")
  String baseUrl;

  @Value("${confluence.username:}")
  String username;

  @Value("${confluence.api-token:}")
  String apiToken;

  @Value("${confluence.space-key:}")
  String spaceKey;

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getUsername() {
    return username;
  }

  public String getApiToken() {
    return apiToken;
  }

  public String getSpaceKey() {
    return spaceKey;
  }

  public boolean isConfigured() {
    return baseUrl != null
        && !baseUrl.isBlank()
        && username != null
        && !username.isBlank()
        && apiToken != null
        && !apiToken.isBlank()
        && spaceKey != null
        && !spaceKey.isBlank();
  }
}
