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
package com.quenaire.api.rest.auth;

import com.quenaire.ai.auth.KeycloakProvisioningService;
import com.quenaire.ai.auth.RegistrationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Public signup endpoints.
 *
 * <p>Unauthenticated by design — {@code /api/v1/auth/**} is in the permit list. There is no login
 * endpoint here: authentication is Keycloak's, and the app only ever validates the resulting token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@PreAuthorize("permitAll()")
public class AuthController {

  private final RegistrationService registrationService;
  private final KeycloakProvisioningService keycloakProvisioningService;

  public AuthController(
      RegistrationService registrationService,
      KeycloakProvisioningService keycloakProvisioningService) {
    this.registrationService = registrationService;
    this.keycloakProvisioningService = keycloakProvisioningService;
  }

  /** Lets the signup page show the right copy before anyone fills the form. */
  @GetMapping("/signup-status")
  public ResponseEntity<Map<String, Object>> signupStatus() {
    return ResponseEntity.ok(
        Map.of(
            "signupEnabled", registrationService.isSignupEnabled(),
            "identityProviderConfigured", keycloakProvisioningService.isConfigured()));
  }

  public record RegisterRequest(
      String companyName,
      String firstName,
      String lastName,
      String email,
      String password,
      String workspaceSlug) {}

  @PostMapping("/register")
  public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
    RegistrationService.RegistrationResult result =
        registrationService.register(
            request == null
                ? null
                : new RegistrationService.RegistrationRequest(
                    request.companyName(),
                    request.firstName(),
                    request.lastName(),
                    request.email(),
                    request.password(),
                    request.workspaceSlug()));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("success", result.success());

    if (!result.success()) {
      body.put("error", result.error());
      return ResponseEntity.badRequest().body(body);
    }

    body.put("tenantId", result.tenantId());
    body.put("userId", result.userId());
    body.put("workspaceSlug", result.workspaceSlug());
    body.put("credentialsProvisioned", result.credentialsProvisioned());
    body.put("message", result.message());
    return ResponseEntity.ok(body);
  }

  /** Live availability check for the workspace address field. */
  @GetMapping("/workspace-available")
  public ResponseEntity<Map<String, Object>> workspaceAvailable(@RequestParam String slug) {
    return ResponseEntity.ok(
        Map.of("slug", slug, "available", registrationService.isSlugAvailable(slug)));
  }

  /** Suggest a free workspace address derived from a company name. */
  @GetMapping("/suggest-workspace")
  public ResponseEntity<Map<String, Object>> suggestWorkspace(@RequestParam String companyName) {
    String suggestion = registrationService.suggestSlug(companyName);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("companyName", companyName);
    body.put("suggestion", suggestion);
    return ResponseEntity.ok(body);
  }
}
