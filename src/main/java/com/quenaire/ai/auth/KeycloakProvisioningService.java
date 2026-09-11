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
package com.quenaire.ai.auth;

import com.quenaire.domain.tenant.Tenant;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates the identity behind a new workspace owner in Keycloak.
 *
 * <p>The application never stores passwords: the plaintext travels from the signup form to Keycloak
 * and is not persisted here. Tenant identity is carried as a JWT claim ({@code tenant_id} / {@code
 * tenant_slug}) so {@code TenantContextFilter} can resolve it on every later request.
 *
 * <p>Every method degrades to {@link Optional#empty()} rather than throwing when Keycloak is absent
 * or misconfigured — signup must still create the workspace on an instance where the identity
 * provider has not been wired up yet.
 */
@Service
public class KeycloakProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(KeycloakProvisioningService.class);

  private final String adminUrl;
  private final String adminRealm;
  private final String adminClientId;
  private final String adminClientSecret;
  private final String realm;

  public KeycloakProvisioningService(
      @Value("${app.keycloak.admin-url:}") String adminUrl,
      @Value("${app.keycloak.admin-realm:master}") String adminRealm,
      @Value("${app.keycloak.admin-client-id:}") String adminClientId,
      @Value("${app.keycloak.admin-client-secret:}") String adminClientSecret,
      @Value("${app.keycloak.realm:quenaire}") String realm) {
    this.adminUrl = adminUrl;
    this.adminRealm = adminRealm;
    this.adminClientId = adminClientId;
    this.adminClientSecret = adminClientSecret;
    this.realm = realm;
  }

  public boolean isConfigured() {
    return notBlank(adminUrl) && notBlank(adminClientId) && notBlank(adminClientSecret);
  }

  /**
   * Create the owner account and grant it the OWNER realm role.
   *
   * @return the Keycloak user id, or empty if Keycloak is unavailable
   */
  public Optional<String> provisionOwner(
      String email, String firstName, String lastName, String password, Tenant tenant) {
    if (!isConfigured()) {
      log.info(
          "Keycloak is not configured; workspace {} created without provisioned credentials",
          tenant.getSlug());
      return Optional.empty();
    }

    try (Keycloak keycloak = adminClient()) {
      RealmResource realmResource = keycloak.realm(realm);
      UsersResource users = realmResource.users();

      if (!users.searchByEmail(email, true).isEmpty()) {
        log.warn("Keycloak already has a user for {}", email);
        return Optional.empty();
      }

      UserRepresentation user = new UserRepresentation();
      user.setUsername(email);
      user.setEmail(email);
      user.setFirstName(firstName);
      user.setLastName(lastName);
      user.setEnabled(true);
      user.setEmailVerified(false);
      // TenantContextFilter reads these claims to scope every request.
      user.setAttributes(
          Map.of(
              "tenant_id", List.of(tenant.getId().toString()),
              "tenant_slug", List.of(tenant.getSlug())));

      String userId;
      try (Response response = users.create(user)) {
        if (response.getStatus() != 201) {
          log.warn("Keycloak rejected user creation for {}: HTTP {}", email, response.getStatus());
          return Optional.empty();
        }
        userId = extractId(response);
      }

      if (userId == null) {
        return Optional.empty();
      }

      CredentialRepresentation credential = new CredentialRepresentation();
      credential.setType(CredentialRepresentation.PASSWORD);
      credential.setValue(password);
      credential.setTemporary(false);
      users.get(userId).resetPassword(credential);

      assignRealmRole(realmResource, userId, "OWNER");

      log.info("Provisioned Keycloak owner {} for workspace {}", email, tenant.getSlug());
      return Optional.of(userId);

    } catch (Exception e) {
      // Never fail the signup over the identity provider being down.
      log.warn("Could not provision Keycloak user for {}: {}", email, e.getMessage());
      return Optional.empty();
    }
  }

  private void assignRealmRole(RealmResource realmResource, String userId, String roleName) {
    try {
      RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
      realmResource.users().get(userId).roles().realmLevel().add(List.of(role));
    } catch (Exception e) {
      log.warn("Could not assign realm role {} to {}: {}", roleName, userId, e.getMessage());
    }
  }

  private Keycloak adminClient() {
    return KeycloakBuilder.builder()
        .serverUrl(adminUrl)
        .realm(adminRealm)
        .grantType("client_credentials")
        .clientId(adminClientId)
        .clientSecret(adminClientSecret)
        .build();
  }

  /** Keycloak returns the new user's id only in the Location header. */
  private static String extractId(Response response) {
    String location = response.getHeaderString("Location");
    if (location == null || !location.contains("/")) {
      return null;
    }
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
