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
package com.quenaire.security;

import com.quenaire.domain.tenant.TenantRepository;
import com.quenaire.domain.user.User;
import com.quenaire.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);
  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String TENANT_CLAIM = "tenant_id";

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
      resolveTenantAndUser(jwtAuth.getToken());
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  /**
   * Establishes whose data this request may touch.
   *
   * <p>The authoritative answer is the user row: a signed-in user works in their own tenant and
   * nobody else's. The {@code tenant_id} claim and the {@code X-Tenant-ID} header are, at most,
   * assertions made by the caller.
   *
   * <p>That mattered because the header was being trusted. Tenancy fell back to {@code X-Tenant-ID}
   * whenever the token carried no {@code tenant_id} claim -- which was always, since no Keycloak
   * mapper emitted one -- and nothing checked the caller was entitled to the tenant they named. The
   * user was then refused if it did not match, but only the user: the tenant stayed set, and every
   * tenant-scoped query in the application ran against it. Any signed-in customer could read
   * another customer's questionnaires by naming their id in a header.
   *
   * <p>A token with no user row is a service account. Those get no tenant here; a background caller
   * that needs one passes it explicitly to the service it calls.
   */
  private void resolveTenantAndUser(Jwt jwt) {
    String keycloakId = jwt.getSubject();
    if (!StringUtils.hasText(keycloakId)) {
      return;
    }

    Optional<User> found = userRepository.findByKeycloakId(keycloakId);
    if (found.isEmpty()) {
      log.debug("No user row for JWT subject {}; no tenant context established", keycloakId);
      return;
    }

    User user = found.get();

    // If the caller named a tenant, it must be their own. Saying so in the log makes a
    // probe visible rather than silent.
    String claimed = jwt.getClaim(TENANT_CLAIM);
    String header = requestedTenantHeader();
    for (String assertion : new String[] {claimed, header}) {
      if (StringUtils.hasText(assertion) && !assertion.equals(String.valueOf(user.getTenantId()))) {
        log.warn(
            "JWT subject {} asked for tenant {} but belongs to {}; using their own",
            keycloakId,
            assertion,
            user.getTenantId());
      }
    }

    tenantRepository
        .findById(user.getTenantId())
        .filter(tenant -> !tenant.isDeleted())
        .ifPresentOrElse(
            TenantContext::setCurrentTenant,
            () -> log.warn("User {} belongs to a missing or deleted tenant", keycloakId));

    if (TenantContext.hasTenant()) {
      TenantContext.setCurrentUser(user);
    }
  }

  /** Only read so that a mismatch can be logged; it no longer decides anything. */
  private String requestedTenantHeader() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes == null ? null : attributes.getRequest().getHeader(TENANT_HEADER);
  }
}
