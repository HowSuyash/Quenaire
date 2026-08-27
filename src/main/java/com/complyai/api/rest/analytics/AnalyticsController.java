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
package com.complyai.api.rest.analytics;

import com.complyai.ai.analytics.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Analytics and ROI reporting for the current tenant. */
@RestController
@RequestMapping("/api/vanta/analytics")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
public class AnalyticsController {

  /** Cap the trend window so one request cannot scan an unbounded history. */
  private static final int MAX_DAYS = 365;

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping("/dashboard")
  public ResponseEntity<AnalyticsService.Dashboard> dashboard(
      @RequestParam(defaultValue = "30") int days) {
    return ResponseEntity.ok(analyticsService.getDashboard(Math.min(Math.max(days, 1), MAX_DAYS)));
  }

  @GetMapping("/roi")
  public ResponseEntity<AnalyticsService.Roi> roi(@RequestParam(defaultValue = "30") int days) {
    return ResponseEntity.ok(
        analyticsService.getDashboard(Math.min(Math.max(days, 1), MAX_DAYS)).roi());
  }
}
