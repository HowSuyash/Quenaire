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
package com.complyai.integration.jira;

import com.complyai.domain.questionnaire.Question;
import com.complyai.domain.questionnaire.Questionnaire;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JiraIntegrationService {

  private static final Logger log = LoggerFactory.getLogger(JiraIntegrationService.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final JiraConfig config;
  private final OkHttpClient client;
  private final ObjectMapper objectMapper;

  public JiraIntegrationService(JiraConfig config, ObjectMapper objectMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(
                chain -> {
                  Request original = chain.request();
                  Request.Builder builder =
                      original
                          .newBuilder()
                          .header("Authorization", "Basic " + getAuthHeader())
                          .header("Accept", "application/json")
                          .header("Content-Type", "application/json");
                  return chain.proceed(builder.build());
                })
            .build();
  }

  private String getAuthHeader() {
    String auth = config.getUsername() + ":" + config.getApiToken();
    return Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
  }

  public boolean isConfigured() {
    return config.isConfigured();
  }

  public Optional<String> createIssue(Questionnaire questionnaire, List<Question> questions) {
    if (!isConfigured()) {
      log.warn("Jira integration not configured");
      return Optional.empty();
    }

    try {
      String summary = "[ComplyAI] " + questionnaire.getTitle();
      String description = buildDescription(questionnaire, questions);

      Map<String, Object> fields =
          Map.of(
              "project",
              Map.of("key", config.getProjectKey()),
              "summary",
              summary,
              "description",
              description,
              "issuetype",
              Map.of("name", "Task"));

      Map<String, Object> payload = Map.of("fields", fields);
      String json = objectMapper.writeValueAsString(payload);

      Request request =
          new Request.Builder()
              .url(config.getBaseUrl() + "/rest/api/2/issue")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          log.error(
              "Failed to create Jira issue: {} - {}",
              response.code(),
              response.body() != null ? response.body().string() : "no body");
          return Optional.empty();
        }
        JsonNode node = objectMapper.readTree(response.body().string());
        String issueKey = node.get("key").asText();
        log.info("Created Jira issue: {}", issueKey);
        return Optional.of(issueKey);
      }
    } catch (IOException e) {
      log.error("Error creating Jira issue", e);
      return Optional.empty();
    }
  }

  public Optional<String> createSubTask(String parentIssueKey, Question question, String answer) {
    if (!isConfigured()) {
      return Optional.empty();
    }

    try {
      String summary =
          String.format(
              "[Q%s] %s", question.getQuestionNumber(), truncate(question.getText(), 100));
      String description =
          String.format(
              "**Question:** %s\n\n**Answer:** %s\n\n**Type:** %s",
              question.getText(),
              answer != null ? answer : "Not answered",
              question.getQuestionType());

      Map<String, Object> fields =
          Map.of(
              "project", Map.of("key", config.getProjectKey()),
              "parent", Map.of("key", parentIssueKey),
              "summary", summary,
              "description", description,
              "issuetype", Map.of("name", "Sub-task"));

      Map<String, Object> payload = Map.of("fields", fields);
      String json = objectMapper.writeValueAsString(payload);

      Request request =
          new Request.Builder()
              .url(config.getBaseUrl() + "/rest/api/2/issue")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          log.error("Failed to create Jira sub-task: {}", response.code());
          return Optional.empty();
        }
        JsonNode node = objectMapper.readTree(response.body().string());
        return Optional.of(node.get("key").asText());
      }
    } catch (IOException e) {
      log.error("Error creating Jira sub-task", e);
      return Optional.empty();
    }
  }

  public Optional<JsonNode> getIssue(String issueKey) {
    if (!isConfigured()) return Optional.empty();

    try {
      Request request =
          new Request.Builder()
              .url(config.getBaseUrl() + "/rest/api/2/issue/" + issueKey)
              .get()
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) return Optional.empty();
        return Optional.of(objectMapper.readTree(response.body().string()));
      }
    } catch (IOException e) {
      log.error("Error fetching Jira issue", e);
      return Optional.empty();
    }
  }

  public boolean addComment(String issueKey, String comment) {
    if (!isConfigured()) return false;

    try {
      Map<String, Object> payload = Map.of("body", comment);
      String json = objectMapper.writeValueAsString(payload);

      Request request =
          new Request.Builder()
              .url(config.getBaseUrl() + "/rest/api/2/issue/" + issueKey + "/comment")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        return response.isSuccessful();
      }
    } catch (IOException e) {
      log.error("Error adding Jira comment", e);
      return false;
    }
  }

  public boolean transitionIssue(String issueKey, String transitionName) {
    if (!isConfigured()) return false;

    try {
      Request getTransitions =
          new Request.Builder()
              .url(config.getBaseUrl() + "/rest/api/2/issue/" + issueKey + "/transitions")
              .get()
              .build();

      try (Response response = client.newCall(getTransitions).execute()) {
        if (!response.isSuccessful()) return false;
        JsonNode transitions = objectMapper.readTree(response.body().string());
        for (JsonNode t : transitions.get("transitions")) {
          if (t.get("name").asText().equalsIgnoreCase(transitionName)) {
            String transitionId = t.get("id").asText();
            Map<String, Object> payload = Map.of("transition", Map.of("id", transitionId));
            String json = objectMapper.writeValueAsString(payload);

            Request postTransition =
                new Request.Builder()
                    .url(config.getBaseUrl() + "/rest/api/2/issue/" + issueKey + "/transitions")
                    .post(RequestBody.create(json, JSON))
                    .build();

            try (Response r = client.newCall(postTransition).execute()) {
              return r.isSuccessful();
            }
          }
        }
      }
    } catch (IOException e) {
      log.error("Error transitioning Jira issue", e);
    }
    return false;
  }

  private String buildDescription(Questionnaire questionnaire, List<Question> questions) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("**Questionnaire:** %s\n", questionnaire.getTitle()));
    if (questionnaire.getDescription() != null) {
      sb.append(String.format("**Description:** %s\n", questionnaire.getDescription()));
    }
    sb.append(String.format("**Total Questions:** %d\n\n", questions.size()));
    sb.append("---\n\n");
    for (Question q : questions) {
      sb.append(String.format("**Q%s:** %s\n", q.getQuestionNumber(), q.getText()));
      sb.append(
          String.format(
              "*Type: %s | Required: %s*\n\n",
              q.getQuestionType(), q.getIsRequired() ? "Yes" : "No"));
    }
    sb.append("---\n*Created via ComplyAI Security Questionnaire Automation*");
    return sb.toString();
  }

  private String truncate(String text, int maxLength) {
    if (text == null) return "";
    return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
  }
}
