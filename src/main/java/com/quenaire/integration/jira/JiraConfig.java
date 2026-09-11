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
package com.quenaire.integration.jira;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JiraConfig {

  @Value("${jira.base-url:}")
  String baseUrl;

  @Value("${jira.username:}")
  String username;

  @Value("${jira.api-token:}")
  String apiToken;

  @Value("${jira.project-key:}")
  String projectKey;

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getUsername() {
    return username;
  }

  public String getApiToken() {
    return apiToken;
  }

  public String getProjectKey() {
    return projectKey;
  }

  public boolean isConfigured() {
    return baseUrl != null
        && !baseUrl.isBlank()
        && username != null
        && !username.isBlank()
        && apiToken != null
        && !apiToken.isBlank();
  }
}
