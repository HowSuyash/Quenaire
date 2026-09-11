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
package com.quenaire.integration.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quenaire.domain.questionnaire.Question;
import com.quenaire.domain.questionnaire.Questionnaire;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubIntegrationService {

  private static final Logger log = LoggerFactory.getLogger(GitHubIntegrationService.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final GitHubConfig config;
  private final OkHttpClient client;
  private final ObjectMapper objectMapper;

  public GitHubIntegrationService(GitHubConfig config, ObjectMapper objectMapper) {
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
                          .header("Authorization", "Bearer " + config.getToken())
                          .header("Accept", "application/vnd.github+json")
                          .header("X-GitHub-Api-Version", "2022-11-28");
                  return chain.proceed(builder.build());
                })
            .build();
  }

  public boolean isConfigured() {
    return config.isConfigured();
  }

  public Optional<String> createIssue(Questionnaire questionnaire, List<Question> questions) {
    if (!isConfigured()) {
      log.warn("GitHub integration not configured");
      return Optional.empty();
    }

    try {
      String title = "[Quenaire] " + questionnaire.getTitle();
      String body = buildIssueBody(questionnaire, questions);

      Map<String, Object> payload =
          Map.of(
              "title", title,
              "body", body,
              "labels", List.of("security-questionnaire", "quenaire"));

      String json = objectMapper.writeValueAsString(payload);

      Request request =
          new Request.Builder()
              .url(
                  config.getBaseUrl()
                      + "/repos/"
                      + config.getOwner()
                      + "/"
                      + config.getRepository()
                      + "/issues")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          log.error(
              "Failed to create GitHub issue: {} - {}",
              response.code(),
              response.body() != null ? response.body().string() : "no body");
          return Optional.empty();
        }
        JsonNode node = objectMapper.readTree(response.body().string());
        int issueNumber = node.get("number").asInt();
        log.info("Created GitHub issue #{}", issueNumber);
        return Optional.of(String.valueOf(issueNumber));
      }
    } catch (IOException e) {
      log.error("Error creating GitHub issue", e);
      return Optional.empty();
    }
  }

  public Optional<String> createSubIssue(int parentIssueNumber, Question question, String answer) {
    if (!isConfigured()) return Optional.empty();

    try {
      String title =
          String.format(
              "[Q%s] %s", question.getQuestionNumber(), truncate(question.getText(), 100));
      String body =
          String.format(
              "**Question:** %s\n\n**Answer:** %s\n\n**Type:** %s\n\n*Parent: #%d*",
              question.getText(),
              answer != null ? answer : "Not answered",
              question.getQuestionType(),
              parentIssueNumber);

      Map<String, Object> payload =
          Map.of(
              "title", title,
              "body", body,
              "labels", List.of("security-questionnaire", "quenaire", "sub-issue"));

      String json = objectMapper.writeValueAsString(payload);

      Request request =
          new Request.Builder()
              .url(
                  config.getBaseUrl()
                      + "/repos/"
                      + config.getOwner()
                      + "/"
                      + config.getRepository()
                      + "/issues")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          log.error("Failed to create GitHub sub-issue: {}", response.code());
          return Optional.empty();
        }
        JsonNode node = objectMapper.readTree(response.body().string());
        return Optional.of(String.valueOf(node.get("number").asInt()));
      }
    } catch (IOException e) {
      log.error("Error creating GitHub sub-issue", e);
      return Optional.empty();
    }
  }

  public Optional<JsonNode> getIssue(int issueNumber) {
    if (!isConfigured()) return Optional.empty();

    try {
      Request request =
          new Request.Builder()
              .url(
                  config.getBaseUrl()
                      + "/repos/"
                      + config.getOwner()
                      + "/"
                      + config.getRepository()
                      + "/issues/"
                      + issueNumber)
              .get()
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) return Optional.empty();
        return Optional.of(objectMapper.readTree(response.body().string()));
      }
    } catch (IOException e) {
      log.error("Error fetching GitHub issue", e);
      return Optional.empty();
    }
  }

  public boolean addComment(int issueNumber, String comment) {
    if (!isConfigured()) return false;

    try {
      Map<String, Object> payload = Map.of("body", comment);
      String json = objectMapper.writeValueAsString(payload);

      Request request =
          new Request.Builder()
              .url(
                  config.getBaseUrl()
                      + "/repos/"
                      + config.getOwner()
                      + "/"
                      + config.getRepository()
                      + "/issues/"
                      + issueNumber
                      + "/comments")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        return response.isSuccessful();
      }
    } catch (IOException e) {
      log.error("Error adding GitHub comment", e);
      return false;
    }
  }

  public boolean closeIssue(int issueNumber) {
    if (!isConfigured()) return false;

    try {
      Map<String, Object> payload = Map.of("state", "closed");
      String json = objectMapper.writeValueAsString(payload);

      Request request =
          new Request.Builder()
              .url(
                  config.getBaseUrl()
                      + "/repos/"
                      + config.getOwner()
                      + "/"
                      + config.getRepository()
                      + "/issues/"
                      + issueNumber)
              .patch(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        return response.isSuccessful();
      }
    } catch (IOException e) {
      log.error("Error closing GitHub issue", e);
      return false;
    }
  }

  public Optional<String> createRepositoryDispatch(String eventType, Map<String, Object> payload) {
    if (!isConfigured()) return Optional.empty();

    try {
      Map<String, Object> dispatchPayload =
          Map.of(
              "event_type", eventType,
              "client_payload", payload);
      String json = objectMapper.writeValueAsString(dispatchPayload);

      Request request =
          new Request.Builder()
              .url(
                  config.getBaseUrl()
                      + "/repos/"
                      + config.getOwner()
                      + "/"
                      + config.getRepository()
                      + "/dispatches")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          log.error("Failed to create repository dispatch: {}", response.code());
          return Optional.empty();
        }
        return Optional.of("dispatched");
      }
    } catch (IOException e) {
      log.error("Error creating repository dispatch", e);
      return Optional.empty();
    }
  }

  private String buildIssueBody(Questionnaire questionnaire, List<Question> questions) {
    StringBuilder sb = new StringBuilder();
    sb.append("## Security Questionnaire\n\n");
    if (questionnaire.getDescription() != null) {
      sb.append(questionnaire.getDescription()).append("\n\n");
    }
    sb.append("**Total Questions:** ").append(questions.size()).append("\n\n");
    sb.append("---\n\n");

    for (int i = 0; i < questions.size(); i++) {
      Question q = questions.get(i);
      sb.append("### Q").append(i + 1).append(": ").append(q.getQuestionNumber()).append("\n\n");
      sb.append(q.getText()).append("\n\n");
      sb.append("*Type: ")
          .append(q.getQuestionType())
          .append(" | Required: ")
          .append(q.getIsRequired() ? "Yes" : "No")
          .append("*\n\n");
      if (q.getOptionsJson() != null && !q.getOptionsJson().isEmpty()) {
        sb.append("**Options:**\n");
        try {
          var options = objectMapper.readTree(q.getOptionsJson());
          for (JsonNode opt : options) {
            sb.append("- ").append(opt.asText()).append("\n");
          }
        } catch (IOException ignored) {
        }
        sb.append("\n");
      }
      sb.append("---\n\n");
    }
    sb.append("*Created via Quenaire Security Questionnaire Automation*");
    return sb.toString();
  }

  private String truncate(String text, int maxLength) {
    if (text == null) return "";
    return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
  }
}
