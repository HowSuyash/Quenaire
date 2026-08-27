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
package com.complyai.integration.loopio;

import com.complyai.domain.questionnaire.vanta.VantaQuestion;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaire;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pushes approved answers into a Loopio project so the customer's RFP team sees them in the tool
 * they already work in.
 *
 * <p>Loopio's API is OAuth2 client-credentials with short-lived bearer tokens; the token is cached
 * until shortly before expiry rather than fetched per call.
 */
@Service
public class LoopioIntegrationService {

  private static final Logger log = LoggerFactory.getLogger(LoopioIntegrationService.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  /** Refresh this far before actual expiry to avoid racing a request. */
  private static final long TOKEN_SKEW_SECONDS = 60;

  private final LoopioConfig config;
  private final ObjectMapper objectMapper;
  private final OkHttpClient client;

  private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

  private record CachedToken(String value, Instant expiresAt) {
    boolean isUsable() {
      return value != null && Instant.now().isBefore(expiresAt.minusSeconds(TOKEN_SKEW_SECONDS));
    }
  }

  public LoopioIntegrationService(LoopioConfig config, ObjectMapper objectMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
  }

  public boolean isConfigured() {
    return config.isConfigured();
  }

  public record SubmitResult(
      boolean success, String projectId, int submitted, int failed, List<String> errors) {}

  /**
   * Write every answered question into a Loopio project.
   *
   * <p>Partial success is normal — one rejected entry should not discard the rest — so the result
   * reports counts rather than throwing.
   */
  public SubmitResult submitQuestionnaire(
      VantaQuestionnaire questionnaire, List<VantaQuestion> questions, String projectId) {
    if (!isConfigured()) {
      return new SubmitResult(false, null, 0, 0, List.of("Loopio integration is not configured"));
    }

    Optional<String> token = accessToken();
    if (token.isEmpty()) {
      return new SubmitResult(false, null, 0, 0, List.of("Could not authenticate with Loopio"));
    }

    String targetProject = projectId;
    if (targetProject == null || targetProject.isBlank()) {
      Optional<String> created = createProject(token.get(), questionnaire.getTitle());
      if (created.isEmpty()) {
        return new SubmitResult(false, null, 0, 0, List.of("Could not create a Loopio project"));
      }
      targetProject = created.get();
    }

    int submitted = 0;
    int failed = 0;
    List<String> errors = new ArrayList<>();

    for (VantaQuestion question : questions) {
      String answer =
          question.getFinalAnswer() != null && !question.getFinalAnswer().isBlank()
              ? question.getFinalAnswer()
              : question.getAiAnswer();

      if (answer == null || answer.isBlank()) {
        continue; // Nothing to push yet.
      }

      try {
        if (pushEntry(token.get(), targetProject, question, answer)) {
          submitted++;
        } else {
          failed++;
          errors.add("Question " + question.getQuestionNumber() + " was rejected by Loopio");
        }
      } catch (IOException e) {
        failed++;
        errors.add("Question " + question.getQuestionNumber() + ": " + e.getMessage());
        log.warn("Loopio push failed for question {}", question.getId(), e);
      }
    }

    log.info(
        "Loopio submit for questionnaire {}: {} ok, {} failed",
        questionnaire.getId(),
        submitted,
        failed);
    return new SubmitResult(failed == 0, targetProject, submitted, failed, errors);
  }

  /** Pull a Loopio project's questions so they can be answered here. */
  public List<Map<String, Object>> importProjectQuestions(String projectId) {
    Optional<String> token = accessToken();
    if (token.isEmpty()) {
      return List.of();
    }

    Request request =
        new Request.Builder()
            .url(config.getBaseUrl() + "/data/v2/projects/" + projectId + "/entries")
            .header("Authorization", "Bearer " + token.get())
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        log.warn("Loopio project import failed: HTTP {}", response.code());
        return List.of();
      }

      JsonNode root = objectMapper.readTree(response.body().string());
      JsonNode items = root.path("items");

      List<Map<String, Object>> questions = new ArrayList<>();
      for (JsonNode item : items) {
        questions.add(
            Map.of(
                "loopioEntryId", item.path("id").asText(),
                "questionNumber", item.path("questionNumber").asText(""),
                "text", item.path("question").asText(""),
                "section", item.path("sectionName").asText("")));
      }
      return questions;
    } catch (IOException e) {
      log.warn("Loopio project import failed", e);
      return List.of();
    }
  }

  public Optional<Map<String, Object>> getStatus() {
    if (!isConfigured()) {
      return Optional.of(Map.of("connected", false, "reason", "Not configured"));
    }
    return Optional.of(
        Map.of(
            "connected", accessToken().isPresent(),
            "baseUrl", config.getBaseUrl(),
            "defaultStackId",
                config.getDefaultStackId() != null ? config.getDefaultStackId() : ""));
  }

  // ---- Internals ---------------------------------------------------------

  private Optional<String> accessToken() {
    CachedToken current = cachedToken.get();
    if (current != null && current.isUsable()) {
      return Optional.of(current.value());
    }

    RequestBody body =
        new FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", config.getClientId())
            .add("client_secret", config.getClientSecret())
            .build();

    Request request = new Request.Builder().url(config.getAuthUrl()).post(body).build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        log.warn("Loopio auth failed: HTTP {}", response.code());
        return Optional.empty();
      }

      JsonNode root = objectMapper.readTree(response.body().string());
      String token = root.path("access_token").asText(null);
      if (token == null) {
        return Optional.empty();
      }

      long expiresIn = root.path("expires_in").asLong(3600);
      cachedToken.set(new CachedToken(token, Instant.now().plusSeconds(expiresIn)));
      return Optional.of(token);
    } catch (IOException e) {
      log.warn("Loopio auth request failed", e);
      return Optional.empty();
    }
  }

  private Optional<String> createProject(String token, String name) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", name != null ? name : "ComplyAI questionnaire");
    payload.put("description", "Imported from ComplyAI");

    try {
      Request request =
          new Request.Builder()
              .url(config.getBaseUrl() + "/data/v2/projects")
              .header("Authorization", "Bearer " + token)
              .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful() || response.body() == null) {
          log.warn("Loopio project creation failed: HTTP {}", response.code());
          return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(response.body().string());
        return Optional.ofNullable(root.path("id").asText(null));
      }
    } catch (IOException e) {
      log.warn("Loopio project creation failed", e);
      return Optional.empty();
    }
  }

  private boolean pushEntry(String token, String projectId, VantaQuestion question, String answer)
      throws IOException {

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("question", question.getText());
    payload.put("answer", answer);
    if (question.getQuestionNumber() != null) {
      payload.put("questionNumber", question.getQuestionNumber());
    }
    if (question.getSection() != null) {
      payload.put("sectionName", question.getSection());
    }
    if (config.getDefaultStackId() != null && !config.getDefaultStackId().isBlank()) {
      payload.put("stackId", config.getDefaultStackId());
    }

    Request request =
        new Request.Builder()
            .url(config.getBaseUrl() + "/data/v2/projects/" + projectId + "/entries")
            .header("Authorization", "Bearer " + token)
            .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
            .build();

    try (Response response = client.newCall(request).execute()) {
      return response.isSuccessful();
    }
  }
}
