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
package com.complyai.integration.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitHubConfig {

  @Value("${github.token:}")
  String token;

  @Value("${github.owner:}")
  String owner;

  @Value("${github.repository:}")
  String repository;

  @Value("${github.base-url:https://api.github.com}")
  String baseUrl;

  public String getToken() {
    return token;
  }

  public String getOwner() {
    return owner;
  }

  public String getRepository() {
    return repository;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public boolean isConfigured() {
    return token != null
        && !token.isBlank()
        && owner != null
        && !owner.isBlank()
        && repository != null
        && !repository.isBlank();
  }
}
