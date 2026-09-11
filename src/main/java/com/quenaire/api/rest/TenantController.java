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

import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.tenant.TenantPlan;
import com.quenaire.domain.tenant.TenantRepository;
import com.quenaire.domain.tenant.TenantStatus;
import com.quenaire.security.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

  private final TenantRepository tenantRepository;

  public TenantController(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  @PostMapping
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Tenant> create(@RequestBody CreateTenantRequest request) {
    Tenant tenant = new Tenant();
    tenant.setName(request.name());
    tenant.setSlug(request.slug());
    tenant.setPlan(request.plan() != null ? request.plan() : TenantPlan.FREE);
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setSettingsJson("{}");
    tenant.setBrandingJson("{}");

    tenant = tenantRepository.save(tenant);
    return ResponseEntity.ok(tenant);
  }

  @GetMapping
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Page<Tenant>> list(Pageable pageable) {
    return ResponseEntity.ok(tenantRepository.findAll(pageable));
  }

  @GetMapping("/current")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Tenant> getCurrent() {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return tenantRepository
        .findById(tenantId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Tenant> get(@PathVariable UUID id) {
    return tenantRepository
        .findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Tenant> update(
      @PathVariable UUID id, @RequestBody UpdateTenantRequest request) {
    return tenantRepository
        .findById(id)
        .map(
            tenant -> {
              if (request.name() != null) tenant.setName(request.name());
              if (request.customDomain() != null) tenant.setCustomDomain(request.customDomain());
              if (request.status() != null) tenant.setStatus(request.status());
              if (request.plan() != null) tenant.setPlan(request.plan());
              if (request.settingsJson() != null) tenant.setSettingsJson(request.settingsJson());
              if (request.brandingJson() != null) tenant.setBrandingJson(request.brandingJson());
              return ResponseEntity.ok(tenantRepository.save(tenant));
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    return tenantRepository
        .findById(id)
        .map(
            tenant -> {
              tenant.setDeletedAt(java.time.Instant.now());
              tenantRepository.save(tenant);
              return ResponseEntity.noContent().<Void>build();
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/stats")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Map<String, Object>> stats() {
    long total = tenantRepository.count();
    long active = tenantRepository.countByStatus(TenantStatus.ACTIVE);
    long trial = tenantRepository.countByStatus(TenantStatus.TRIAL);
    long suspended = tenantRepository.countByStatus(TenantStatus.SUSPENDED);
    long expired = tenantRepository.countByStatus(TenantStatus.EXPIRED);

    return ResponseEntity.ok(
        Map.of(
            "total", total,
            "active", active,
            "trial", trial,
            "suspended", suspended,
            "expired", expired));
  }

  public record CreateTenantRequest(String name, String slug, TenantPlan plan) {}

  public record UpdateTenantRequest(
      String name,
      String customDomain,
      TenantStatus status,
      TenantPlan plan,
      String settingsJson,
      String brandingJson) {}
}
