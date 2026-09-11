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
package com.quenaire.api.rest;

import com.quenaire.domain.user.User;
import com.quenaire.domain.user.UserRepository;
import com.quenaire.domain.user.UserRole;
import com.quenaire.domain.user.UserStatus;
import com.quenaire.security.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserRepository userRepository;

  public UserController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ResponseEntity<User> create(@RequestBody CreateUserRequest request) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    User user = new User();
    user.setEmail(request.email());
    user.setFirstName(request.firstName());
    user.setLastName(request.lastName());
    user.setRole(request.role() != null ? request.role() : UserRole.MEMBER);
    user.setStatus(request.status() != null ? request.status() : UserStatus.INVITED);
    user.setTenantId(tenantId);
    user.setPreferencesJson("{}");

    user = userRepository.save(user);
    return ResponseEntity.ok(user);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ResponseEntity<Page<User>> list(
      @RequestParam(required = false) UserStatus status,
      @RequestParam(required = false) UserRole role,
      Pageable pageable) {

    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    Page<User> users;
    if (status != null && role != null) {
      users =
          userRepository.findByTenantIdAndStatusAndRoleAndDeletedAtIsNull(
              tenantId, status, role, pageable);
    } else if (status != null) {
      users = userRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, status, pageable);
    } else if (role != null) {
      users = userRepository.findByTenantIdAndRoleAndDeletedAtIsNull(tenantId, role, pageable);
    } else {
      users = userRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    }

    return ResponseEntity.ok(users);
  }

  @GetMapping("/current")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<User> getCurrent() {
    // Current user is available via SecurityContext
    // This would need integration with Spring Security's authentication
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ResponseEntity<User> get(@PathVariable UUID id) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return userRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ResponseEntity<User> update(
      @PathVariable UUID id, @RequestBody UpdateUserRequest request) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return userRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
        .map(
            user -> {
              if (request.firstName() != null) user.setFirstName(request.firstName());
              if (request.lastName() != null) user.setLastName(request.lastName());
              if (request.role() != null) user.setRole(request.role());
              if (request.status() != null) user.setStatus(request.status());
              if (request.preferencesJson() != null)
                user.setPreferencesJson(request.preferencesJson());
              return ResponseEntity.ok(userRepository.save(user));
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return userRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
        .map(
            user -> {
              user.setDeletedAt(java.time.Instant.now());
              userRepository.save(user);
              return ResponseEntity.noContent().<Void>build();
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/stats")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ResponseEntity<Map<String, Object>> stats() {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    long total = userRepository.countByTenantIdAndDeletedAtIsNull(tenantId);
    long active =
        userRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, UserStatus.ACTIVE);
    long invited =
        userRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, UserStatus.INVITED);
    long suspended =
        userRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, UserStatus.SUSPENDED);

    return ResponseEntity.ok(
        Map.of(
            "total", total,
            "active", active,
            "invited", invited,
            "suspended", suspended));
  }

  public record CreateUserRequest(
      String email, String firstName, String lastName, UserRole role, UserStatus status) {}

  public record UpdateUserRequest(
      String firstName,
      String lastName,
      UserRole role,
      UserStatus status,
      String preferencesJson) {}
}
