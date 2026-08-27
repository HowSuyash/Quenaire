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
package com.complyai.security;

import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.tenant.TenantRepository;
import com.complyai.domain.user.User;
import com.complyai.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);
  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String TENANT_SLUG_HEADER = "X-Tenant-Slug";

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;

  public TenantContextFilter(TenantRepository tenantRepository, UserRepository userRepository) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    TenantContext.clear();

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();

      // Try to get tenant from JWT claims (Keycloak custom claim)
      String tenantIdClaim = jwt.getClaim("tenant_id");
      String tenantSlugClaim = jwt.getClaim("tenant_slug");

      UUID tenantId = null;

      if (StringUtils.hasText(tenantIdClaim)) {
        try {
          tenantId = UUID.fromString(tenantIdClaim);
        } catch (IllegalArgumentException e) {
          log.warn("Invalid tenant_id in JWT: {}", tenantIdClaim);
        }
      }

      // Fallback to headers (for API keys, service-to-service)
      if (tenantId == null) {
        String headerTenantId = request.getHeader(TENANT_HEADER);
        if (StringUtils.hasText(headerTenantId)) {
          try {
            tenantId = UUID.fromString(headerTenantId);
          } catch (IllegalArgumentException e) {
            log.warn("Invalid X-Tenant-ID header: {}", headerTenantId);
          }
        }
      }

      if (tenantId == null && StringUtils.hasText(tenantSlugClaim)) {
        Optional<Tenant> tenant = tenantRepository.findActiveBySlug(tenantSlugClaim);
        if (tenant.isPresent()) {
          tenantId = tenant.get().getId();
        }
      }

      if (tenantId == null) {
        String headerTenantSlug = request.getHeader(TENANT_SLUG_HEADER);
        if (StringUtils.hasText(headerTenantSlug)) {
          Optional<Tenant> tenant = tenantRepository.findActiveBySlug(headerTenantSlug);
          if (tenant.isPresent()) {
            tenantId = tenant.get().getId();
          }
        }
      }

      final UUID finalTenantId = tenantId;
      if (finalTenantId != null) {
        tenantRepository
            .findById(finalTenantId)
            .filter(t -> !t.isDeleted())
            .ifPresentOrElse(
                t -> TenantContext.setCurrentTenant(t),
                () -> log.warn("Tenant not found or deleted: {}", finalTenantId));
      }

      resolveCurrentUser(jwt);
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  /**
   * Bind the authenticated user to the request so attribution fields record who actually acted. The
   * JWT subject is the Keycloak id; the user must belong to the resolved tenant or we leave the
   * context empty rather than trusting a cross-tenant token.
   */
  private void resolveCurrentUser(Jwt jwt) {
    String keycloakId = jwt.getSubject();
    if (!StringUtils.hasText(keycloakId)) {
      return;
    }

    Optional<User> user = userRepository.findByKeycloakId(keycloakId);
    if (user.isEmpty()) {
      log.debug("No user row for JWT subject {}", keycloakId);
      return;
    }

    UUID tenantId = TenantContext.getCurrentTenantId();
    User resolved = user.get();
    if (tenantId != null && !tenantId.equals(resolved.getTenantId())) {
      log.warn(
          "JWT subject {} belongs to tenant {} but request resolved tenant {}; ignoring user",
          keycloakId,
          resolved.getTenantId(),
          tenantId);
      return;
    }

    TenantContext.setCurrentUser(resolved);
  }
}
