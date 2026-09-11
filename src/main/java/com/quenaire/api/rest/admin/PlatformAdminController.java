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
package com.quenaire.api.rest.admin;

import com.quenaire.ai.admin.PlatformAdminService;
import com.quenaire.domain.tenant.TenantPlan;
import com.quenaire.domain.tenant.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Platform operator portal — cross-tenant, and gated on {@code PLATFORM_ADMIN} rather than any
 * tenant-scoped role.
 *
 * <p>{@code PLATFORM_ADMIN} must be a realm role granted only to staff. A tenant OWNER deliberately
 * cannot reach these endpoints: owning a workspace is not the same as running the platform.
 */
@RestController
@RequestMapping("/api/platform/admin")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminController {

  private final PlatformAdminService platformAdminService;

  public PlatformAdminController(PlatformAdminService platformAdminService) {
    this.platformAdminService = platformAdminService;
  }

  @GetMapping("/stats")
  public ResponseEntity<PlatformAdminService.PlatformStats> stats() {
    return ResponseEntity.ok(platformAdminService.getStats());
  }

  @GetMapping("/tenants")
  public ResponseEntity<List<PlatformAdminService.TenantSummary>> tenants(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(platformAdminService.listTenants(search, status));
  }

  public record StatusRequest(String status) {}

  @PatchMapping("/tenants/{tenantId}/status")
  public ResponseEntity<?> setStatus(
      @PathVariable UUID tenantId, @RequestBody StatusRequest request) {
    try {
      TenantStatus status = TenantStatus.valueOf(request.status().toUpperCase());
      return ResponseEntity.ok(platformAdminService.setTenantStatus(tenantId, status));
    } catch (IllegalArgumentException | NullPointerException e) {
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "success", false, "error", "Unknown status", "supported", TenantStatus.values()));
    }
  }

  public record PlanRequest(String plan, Instant expiresAt) {}

  @PatchMapping("/tenants/{tenantId}/plan")
  public ResponseEntity<?> setPlan(@PathVariable UUID tenantId, @RequestBody PlanRequest request) {
    try {
      TenantPlan plan = TenantPlan.valueOf(request.plan().toUpperCase());
      return ResponseEntity.ok(
          platformAdminService.setTenantPlan(tenantId, plan, request.expiresAt()));
    } catch (IllegalArgumentException | NullPointerException e) {
      return ResponseEntity.badRequest()
          .body(
              Map.of("success", false, "error", "Unknown plan", "supported", TenantPlan.values()));
    }
  }

  @DeleteMapping("/tenants/{tenantId}")
  public ResponseEntity<Map<String, Object>> deleteTenant(@PathVariable UUID tenantId) {
    platformAdminService.softDeleteTenant(tenantId);
    return ResponseEntity.ok(Map.of("success", true));
  }
}
