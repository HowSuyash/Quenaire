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
package com.complyai.api.rest.whitelabel;

import com.complyai.ai.whitelabel.WhitelabelService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Whitelabel branding.
 *
 * <p>{@code /resolve} is public so the login page can theme itself before anyone has authenticated;
 * everything else needs an owner or admin.
 */
@RestController
@RequestMapping("/api/vanta/whitelabel")
public class WhitelabelController {

  private final WhitelabelService whitelabelService;

  public WhitelabelController(WhitelabelService whitelabelService) {
    this.whitelabelService = whitelabelService;
  }

  /**
   * Branding for the host being visited, or an explicit slug.
   *
   * <p>Returns 204 rather than 404 for an unknown host: a missing brand is a normal state (the
   * default theme applies), not an error.
   */
  @GetMapping("/resolve")
  public ResponseEntity<WhitelabelService.PublicBranding> resolve(
      @RequestParam(required = false) String slug, HttpServletRequest request) {

    String host = request.getHeader("X-Forwarded-Host");
    if (host == null || host.isBlank()) {
      host = request.getHeader("Host");
    }

    return whitelabelService
        .resolvePublic(host, slug)
        .map(
            branding ->
                ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                    .body(branding))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/branding")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
  public ResponseEntity<WhitelabelService.Branding> getBranding() {
    return ResponseEntity.ok(whitelabelService.getBranding());
  }

  @PutMapping("/branding")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<?> updateBranding(@RequestBody WhitelabelService.Branding branding) {
    try {
      return ResponseEntity.ok(whitelabelService.updateBranding(branding));
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  public record DomainRequest(String domain) {}

  @PutMapping("/domain")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<Map<String, Object>> setDomain(@RequestBody DomainRequest request) {
    try {
      whitelabelService.setCustomDomain(request != null ? request.domain() : null);
      return ResponseEntity.ok(Map.of("success", true));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }
}
