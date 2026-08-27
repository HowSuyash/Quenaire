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
package com.complyai.api.rest.program;

import com.complyai.ai.program.ControlFrameworkCatalog;
import com.complyai.ai.program.ProgramAwarenessService;
import com.complyai.domain.program.ControlMapping;
import com.complyai.domain.program.ProgramSignal;
import com.complyai.security.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Security Program Awareness: what the connected systems say the program actually does, and how
 * that maps onto compliance frameworks.
 */
@RestController
@RequestMapping("/api/vanta/program")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
public class ProgramAwarenessController {

  private final ProgramAwarenessService programAwarenessService;

  public ProgramAwarenessController(ProgramAwarenessService programAwarenessService) {
    this.programAwarenessService = programAwarenessService;
  }

  /** Pull fresh evidence from every configured connector. */
  @PostMapping("/refresh")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<Map<String, Object>> refresh() {
    try {
      ProgramAwarenessService.RefreshResult result = programAwarenessService.refresh();
      return ResponseEntity.ok(
          Map.of(
              "success", true,
              "collected", result.collected(),
              "created", result.created(),
              "updated", result.updated(),
              "mappingsCreated", result.mappingsCreated(),
              "bySource", result.bySource(),
              "skipped", result.skipped()));
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> health() {
    return ResponseEntity.ok(programAwarenessService.getProgramHealth());
  }

  @GetMapping("/signals")
  public ResponseEntity<List<ProgramSignal>> signals() {
    return ResponseEntity.ok(programAwarenessService.listSignals());
  }

  @GetMapping("/coverage")
  public ResponseEntity<List<ProgramAwarenessService.CoverageSummary>> coverage() {
    return ResponseEntity.ok(programAwarenessService.getAllCoverage());
  }

  @GetMapping("/coverage/{framework}")
  public ResponseEntity<?> coverageFor(@PathVariable String framework) {
    try {
      ControlMapping.Framework parsed =
          ControlMapping.Framework.valueOf(framework.toUpperCase().replace('-', '_'));
      return ResponseEntity.ok(programAwarenessService.getCoverage(parsed));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "error",
                  "Unknown framework: " + framework,
                  "supported",
                  ControlMapping.Framework.values()));
    }
  }

  /** The control catalogue itself — useful for building framework pickers. */
  @GetMapping("/frameworks")
  public ResponseEntity<Map<String, Object>> frameworks() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("frameworks", ControlMapping.Framework.values());
    body.put(
        "controlCounts",
        ControlFrameworkCatalog.controlCounts().entrySet().stream()
            .collect(
                LinkedHashMap::new, (m, e) -> m.put(e.getKey().name(), e.getValue()), Map::putAll));
    return ResponseEntity.ok(body);
  }

  /**
   * Evidence relevant to a question — what the answer generator cites so a claim can be traced to a
   * dated observation.
   */
  @GetMapping("/facts")
  public ResponseEntity<List<ProgramAwarenessService.ProgramFact>> facts(
      @RequestParam String question, @RequestParam(defaultValue = "5") int limit) {
    return ResponseEntity.ok(
        programAwarenessService.findRelevantFacts(question, Math.min(limit, 25)));
  }

  @PostMapping("/mappings/{mappingId}/confirm")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<Map<String, Object>> confirmMapping(@PathVariable UUID mappingId) {
    programAwarenessService.confirmMapping(mappingId, TenantContext.getCurrentUserId());
    return ResponseEntity.ok(Map.of("success", true));
  }

  @DeleteMapping("/mappings/{mappingId}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
  public ResponseEntity<Map<String, Object>> removeMapping(@PathVariable UUID mappingId) {
    programAwarenessService.removeMapping(mappingId);
    return ResponseEntity.ok(Map.of("success", true));
  }
}
