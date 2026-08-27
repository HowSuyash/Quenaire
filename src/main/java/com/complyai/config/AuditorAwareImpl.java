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

import com.complyai.security.TenantContext;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("auditorProvider")
public class AuditorAwareImpl implements AuditorAware<String> {

  @Override
  @NonNull
  public Optional<String> getCurrentAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();

      // Prefer Keycloak preferred_username or email
      String username = jwt.getClaim("preferred_username");
      if (username != null) return Optional.of(username);

      String email = jwt.getClaim("email");
      if (email != null) return Optional.of(email);

      String sub = jwt.getSubject();
      if (sub != null) return Optional.of(sub);
    }

    // Fallback to tenant context for system operations
    if (TenantContext.hasTenant()) {
      return Optional.of("system:" + TenantContext.getCurrentTenantId());
    }

    return Optional.of("anonymous");
  }
}
