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
package com.quenaire.integration.slack;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackConfig {

  @Value("${slack.bot-token:}")
  String botToken;

  @Value("${slack.signing-secret:}")
  String signingSecret;

  @Value("${slack.default-channel:}")
  String defaultChannel;

  public String getBotToken() {
    return botToken;
  }

  public String getSigningSecret() {
    return signingSecret;
  }

  public String getDefaultChannel() {
    return defaultChannel;
  }

  public boolean isConfigured() {
    return botToken != null && !botToken.isBlank();
  }
}
