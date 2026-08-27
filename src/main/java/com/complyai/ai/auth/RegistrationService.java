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
package com.complyai.ai.auth;

import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.tenant.TenantPlan;
import com.complyai.domain.tenant.TenantRepository;
import com.complyai.domain.tenant.TenantStatus;
import com.complyai.domain.user.User;
import com.complyai.domain.user.UserRepository;
import com.complyai.domain.user.UserRole;
import com.complyai.domain.user.UserStatus;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-serve signup: creates a workspace and its first owner.
 *
 * <p>Passwords are Keycloak's concern, not ours — this application is an OIDC resource server and
 * deliberately stores no credentials. When Keycloak is configured the account is provisioned there;
 * when it is not (local dev), the workspace is still created and the owner is left {@code INVITED}
 * to finish via SSO. Either way no password ever reaches this database.
 */
@Service
public class RegistrationService {

  private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]{2,}$");
  private static final Pattern SLUG_UNSAFE = Pattern.compile("[^a-z0-9-]");

  /** Reserved so a workspace can never shadow a first-level app route. */
  private static final java.util.Set<String> RESERVED_SLUGS =
      java.util.Set.of(
          "api",
          "admin",
          "dashboard",
          "login",
          "register",
          "signup",
          "trust",
          "intake",
          "platform",
          "www",
          "app",
          "static",
          "assets",
          "health");

  private static final int TRIAL_DAYS = 14;

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final KeycloakProvisioningService keycloakProvisioningService;

  private final boolean signupEnabled;

  public RegistrationService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      KeycloakProvisioningService keycloakProvisioningService,
      @Value("${app.signup.enabled:true}") boolean signupEnabled) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.keycloakProvisioningService = keycloakProvisioningService;
    this.signupEnabled = signupEnabled;
  }

  public record RegistrationRequest(
      String companyName,
      String firstName,
      String lastName,
      String email,
      String password,
      String workspaceSlug) {}

  public record RegistrationResult(
      boolean success,
      String error,
      String tenantId,
      String userId,
      String workspaceSlug,
      /** True when the account is ready to sign in; false means finish via SSO. */
      boolean credentialsProvisioned,
      String message) {
    static RegistrationResult failure(String error) {
      return new RegistrationResult(false, error, null, null, null, false, null);
    }
  }

  public boolean isSignupEnabled() {
    return signupEnabled;
  }

  @Transactional
  public RegistrationResult register(RegistrationRequest request) {
    if (!signupEnabled) {
      return RegistrationResult.failure("Self-serve signup is disabled on this instance.");
    }
    if (request == null) {
      return RegistrationResult.failure("Missing request body.");
    }

    String companyName = trimmed(request.companyName());
    String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);

    if (companyName.isEmpty()) {
      return RegistrationResult.failure("Company name is required.");
    }
    if (!EMAIL.matcher(email).matches()) {
      return RegistrationResult.failure("A valid work email is required.");
    }
    if (request.password() == null || request.password().length() < 12) {
      return RegistrationResult.failure("Password must be at least 12 characters.");
    }

    String slug = resolveSlug(request.workspaceSlug(), companyName);
    if (slug == null) {
      return RegistrationResult.failure("Could not derive a workspace address from that name.");
    }
    if (RESERVED_SLUGS.contains(slug)) {
      return RegistrationResult.failure("That workspace address is reserved.");
    }
    if (tenantRepository.existsBySlug(slug)) {
      return RegistrationResult.failure("That workspace address is already taken.");
    }

    // Signup always creates a new tenant, so the usual per-tenant uniqueness
    // is not enough: an address that already owns a workspace anywhere is a
    // duplicate signup.
    if (userRepository.findFirstByEmailAndDeletedAtIsNull(email).isPresent()) {
      return RegistrationResult.failure("An account already exists for that email.");
    }

    Tenant tenant = new Tenant();
    tenant.setName(companyName);
    tenant.setSlug(slug);
    tenant.setStatus(TenantStatus.TRIAL);
    tenant.setPlan(TenantPlan.FREE);
    tenant.setPlanExpiresAt(Instant.now().plusSeconds(TRIAL_DAYS * 86400L));
    tenant = tenantRepository.save(tenant);

    User owner = new User();
    owner.setEmail(email);
    owner.setFirstName(trimmed(request.firstName()));
    owner.setLastName(trimmed(request.lastName()));
    owner.setRole(UserRole.OWNER);
    owner.setTenant(tenant);

    // Provision credentials in Keycloak if it is wired up. A failure here must
    // not roll back the workspace — the owner can still be invited manually.
    Optional<String> keycloakId =
        keycloakProvisioningService.provisionOwner(
            email,
            trimmed(request.firstName()),
            trimmed(request.lastName()),
            request.password(),
            tenant);

    boolean provisioned = keycloakId.isPresent();
    keycloakId.ifPresent(owner::setKeycloakId);
    owner.setStatus(provisioned ? UserStatus.ACTIVE : UserStatus.INVITED);

    owner = userRepository.save(owner);

    log.info(
        "Registered workspace {} ({}) with owner {} [credentials provisioned: {}]",
        tenant.getName(),
        slug,
        email,
        provisioned);

    return new RegistrationResult(
        true,
        null,
        tenant.getId().toString(),
        owner.getId().toString(),
        slug,
        provisioned,
        provisioned
            ? "Workspace created. You can sign in now."
            : "Workspace created. Identity provider is not configured on this instance, "
                + "so finish setting up sign-in through your administrator.");
  }

  /** True when the slug is free and usable. */
  @Transactional(readOnly = true)
  public boolean isSlugAvailable(String candidate) {
    String slug = normalizeSlug(candidate);
    return slug != null && !RESERVED_SLUGS.contains(slug) && !tenantRepository.existsBySlug(slug);
  }

  /** Suggest a free slug for a company name, disambiguating on collision. */
  @Transactional(readOnly = true)
  public String suggestSlug(String companyName) {
    String base = normalizeSlug(companyName);
    if (base == null) {
      return null;
    }
    if (!RESERVED_SLUGS.contains(base) && !tenantRepository.existsBySlug(base)) {
      return base;
    }
    for (int suffix = 2; suffix <= 99; suffix++) {
      String candidate = base + "-" + suffix;
      if (!tenantRepository.existsBySlug(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private String resolveSlug(String requested, String companyName) {
    String slug = normalizeSlug(requested);
    return slug != null ? slug : normalizeSlug(companyName);
  }

  /** Lowercase, hyphenated, URL-safe. Returns null when nothing usable remains. */
  static String normalizeSlug(String raw) {
    if (raw == null) {
      return null;
    }
    String slug =
        SLUG_UNSAFE
            .matcher(raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "-"))
            .replaceAll("")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-|-$", "");

    if (slug.isEmpty() || slug.length() > 63) {
      return slug.isEmpty() ? null : slug.substring(0, 63).replaceAll("-$", "");
    }
    return slug;
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }
}
