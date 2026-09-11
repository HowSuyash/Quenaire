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
package com.quenaire.api.rest.trust;

import com.quenaire.ai.trust.TrustCenterService;
import com.quenaire.domain.trust.TrustCenterItem;
import com.quenaire.domain.trust.TrustCenterLead;
import com.quenaire.domain.trust.TrustCenterProfile;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Trust Center API.
 *
 * <p>Everything under {@code /public} is unauthenticated by design — it backs the prospect-facing
 * page. The administration endpoints below it require a role.
 */
@RestController
@RequestMapping("/api/vanta/trust")
public class TrustCenterController {

  private final TrustCenterService trustCenterService;

  public TrustCenterController(TrustCenterService trustCenterService) {
    this.trustCenterService = trustCenterService;
  }

  // ---- Public --------------------------------------------------------------

  /**
   * The public profile. Returns 404 for both "no such tenant" and "not published" so the endpoint
   * cannot be used to enumerate tenants.
   */
  @GetMapping("/{slug}")
  public ResponseEntity<TrustCenterService.PublicProfile> getPublicProfile(
      @PathVariable String slug) {
    return trustCenterService
        .getPublicProfile(slug)
        .map(
            profile ->
                ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                    .body(profile))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  public record LeadRequest(
      String name,
      String email,
      String company,
      String roleTitle,
      String message,
      UUID requestedItemId,
      String sourceUrl) {}

  @PostMapping("/{slug}/leads")
  public ResponseEntity<Map<String, Object>> submitLead(
      @PathVariable String slug, @RequestBody LeadRequest request, HttpServletRequest httpRequest) {
    TrustCenterService.LeadSubmission submission =
        request == null
            ? null
            : new TrustCenterService.LeadSubmission(
                request.name(),
                request.email(),
                request.company(),
                request.roleTitle(),
                request.message(),
                request.requestedItemId(),
                request.sourceUrl());

    TrustCenterService.LeadResult result =
        trustCenterService.submitLead(slug, submission, clientAddress(httpRequest));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("success", result.success());
    body.put("message", result.message());

    return result.success() ? ResponseEntity.ok(body) : ResponseEntity.badRequest().body(body);
  }

  // ---- Tenant administration ----------------------------------------------

  @GetMapping("/admin/profile")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
  public ResponseEntity<TrustCenterProfile> getMyProfile() {
    return trustCenterService
        .getMyProfile()
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PutMapping("/admin/profile")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<?> upsertProfile(@RequestBody TrustCenterProfile profile) {
    try {
      return ResponseEntity.ok(trustCenterService.upsertProfile(profile));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  public record PublishRequest(boolean published) {}

  @PostMapping("/admin/profile/publish")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<?> publish(@RequestBody PublishRequest request) {
    try {
      TrustCenterProfile profile = trustCenterService.setPublished(request.published());
      return ResponseEntity.ok(
          Map.of(
              "success",
              true,
              "published",
              Boolean.TRUE.equals(profile.getPublished()),
              "url",
              "/trust/" + profile.getSlug()));
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @PostMapping("/admin/items")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<?> addItem(@RequestBody TrustCenterItem item) {
    try {
      return ResponseEntity.ok(trustCenterService.addItem(item));
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @DeleteMapping("/admin/items/{itemId}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<Map<String, Object>> removeItem(@PathVariable UUID itemId) {
    trustCenterService.removeItem(itemId);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @GetMapping("/admin/leads")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
  public ResponseEntity<Map<String, Object>> listLeads(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {

    Page<TrustCenterLead> leads =
        trustCenterService.listLeads(PageRequest.of(page, Math.min(size, 100)));

    return ResponseEntity.ok(
        Map.of(
            "content", leads.getContent(),
            "page", leads.getNumber(),
            "size", leads.getSize(),
            "totalElements", leads.getTotalElements(),
            "totalPages", leads.getTotalPages(),
            "counts", trustCenterService.getLeadCounts()));
  }

  public record LeadStatusRequest(TrustCenterLead.Status status) {}

  @PatchMapping("/admin/leads/{leadId}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
  public ResponseEntity<Map<String, Object>> updateLeadStatus(
      @PathVariable UUID leadId, @RequestBody LeadStatusRequest request) {
    if (request == null || request.status() == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("success", false, "error", "status is required"));
    }
    trustCenterService.updateLeadStatus(leadId, request.status());
    return ResponseEntity.ok(Map.of("success", true));
  }

  // ---- Helpers -------------------------------------------------------------

  /**
   * Best-effort client address for rate limiting. Trusts X-Forwarded-For only because the app is
   * expected to sit behind the operator's own proxy.
   */
  private static String clientAddress(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      List<String> hops = List.of(forwarded.split(","));
      return hops.get(0).trim();
    }
    return request.getRemoteAddr();
  }
}
