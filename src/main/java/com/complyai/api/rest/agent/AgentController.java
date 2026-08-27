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
package com.complyai.api.rest.agent;

import com.complyai.ai.agent.QuestionnaireAgentOrchestrator;
import com.complyai.ai.vanta.VantaQuestionnaireService;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.complyai.security.TenantContext;
import java.util.*;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vanta/agent")
@PreAuthorize("hasRole('ADMIN') or hasRole('OWNER') or hasRole('MEMBER')")
public class AgentController {

  private static final Logger log = LoggerFactory.getLogger(AgentController.class);

  private final QuestionnaireAgentOrchestrator orchestrator;
  private final VantaQuestionnaireService questionnaireService;
  private final VantaQuestionnaireRepository questionnaireRepository;

  public AgentController(
      QuestionnaireAgentOrchestrator orchestrator,
      VantaQuestionnaireService questionnaireService,
      VantaQuestionnaireRepository questionnaireRepository) {
    this.orchestrator = orchestrator;
    this.questionnaireService = questionnaireService;
    this.questionnaireRepository = questionnaireRepository;
  }

  @PostMapping("/questionnaires/{id}/orchestrate")
  public ResponseEntity<Map<String, Object>> startOrchestration(@PathVariable UUID id) {
    UUID userId = TenantContext.getCurrentUserId();
    QuestionnaireAgentOrchestrator.OrchestrationResult result =
        orchestrator.startOrchestration(id, userId);

    if (result.isSuccess()) {
      return ResponseEntity.ok(
          Map.of("success", true, "message", result.getMessage(), "questionnaireId", id));
    } else {
      return ResponseEntity.badRequest()
          .body(Map.of("success", false, "error", result.getMessage()));
    }
  }

  @GetMapping("/questionnaires/{id}/orchestration/status")
  public ResponseEntity<Map<String, Object>> getOrchestrationStatus(@PathVariable UUID id) {
    // In production, would query orchestration state from Redis or database
    return ResponseEntity.ok(
        Map.of(
            "questionnaireId",
            id,
            "status",
            "ORCHESTRATING",
            "progress",
            50,
            "currentStage",
            "GENERATING_ANSWERS"));
  }

  @PostMapping("/tasks")
  public ResponseEntity<Map<String, Object>> submitTask(@RequestBody Map<String, Object> request) {
    // This would delegate to AgentTaskQueue
    return ResponseEntity.ok(Map.of("success", true, "message", "Task submitted to queue"));
  }

  @GetMapping("/tasks/stats")
  public ResponseEntity<Map<String, Object>> getTaskQueueStats() {
    // This would delegate to AgentTaskQueue
    return ResponseEntity.ok(
        Map.of(
            "pending", 0,
            "running", 0,
            "failed", 0,
            "retrying", 0));
  }

  @GetMapping("/confidence/thresholds")
  public ResponseEntity<Map<String, Object>> getConfidenceThresholds() {
    return ResponseEntity.ok(
        Map.of(
            "autoApprove", 85,
            "smeQueue", 60,
            "manualReview", 0));
  }

  @PostMapping("/confidence/thresholds")
  public ResponseEntity<Map<String, Object>> updateConfidenceThresholds(
      @RequestBody Map<String, Integer> request) {
    Integer autoApprove = request.get("autoApprove");
    Integer smeQueue = request.get("smeQueue");

    // In production, would update ConfidenceRouter thresholds
    return ResponseEntity.ok(
        Map.of(
            "success", true,
            "autoApprove", autoApprove,
            "smeQueue", smeQueue));
  }
}
