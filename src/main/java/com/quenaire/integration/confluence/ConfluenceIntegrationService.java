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
package com.quenaire.integration.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quenaire.domain.document.Document;
import com.quenaire.domain.questionnaire.Question;
import com.quenaire.domain.questionnaire.Questionnaire;
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
public class ConfluenceIntegrationService {

  private static final Logger log = LoggerFactory.getLogger(ConfluenceIntegrationService.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final ConfluenceConfig config;
  private final OkHttpClient client;
  private final ObjectMapper objectMapper;

  public ConfluenceIntegrationService(ConfluenceConfig config, ObjectMapper objectMapper) {
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

  public Optional<String> createPage(String title, String content, String parentPageId) {
    if (!isConfigured()) {
      log.warn("Confluence integration not configured");
      return Optional.empty();
    }

    try {
      Map<String, Object> body =
          Map.of(
              "type",
              "page",
              "title",
              title,
              "space",
              Map.of("key", config.getSpaceKey()),
              "body",
              Map.of("storage", Map.of("value", content, "representation", "storage")));

      if (parentPageId != null && !parentPageId.isBlank()) {
        body =
            Map.ofEntries(
                Map.entry("type", "page"),
                Map.entry("title", title),
                Map.entry("space", Map.of("key", config.getSpaceKey())),
                Map.entry(
                    "body",
                    Map.of("storage", Map.of("value", content, "representation", "storage"))),
                Map.entry("ancestors", List.of(Map.of("id", parentPageId))));
      }

      String json = objectMapper.writeValueAsString(body);

      Request request =
          new Request.Builder()
              .url(config.getBaseUrl() + "/rest/api/content")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          log.error(
              "Failed to create Confluence page: {} - {}",
              response.code(),
              response.body() != null ? response.body().string() : "no body");
          return Optional.empty();
        }
        JsonNode node = objectMapper.readTree(response.body().string());
        String pageId = node.get("id").asText();
        log.info("Created Confluence page: {} (ID: {})", title, pageId);
        return Optional.of(pageId);
      }
    } catch (IOException e) {
      log.error("Error creating Confluence page", e);
      return Optional.empty();
    }
  }

  public Optional<String> createQuestionnairePage(
      Questionnaire questionnaire, List<Question> questions) {
    String title = "[Quenaire] " + questionnaire.getTitle();
    String content = buildQuestionnaireContent(questionnaire, questions);
    return createPage(title, content, null);
  }

  public Optional<String> createDocumentPage(Document document) {
    String title = "[Quenaire Doc] " + document.getTitle();
    String content = buildDocumentContent(document);
    return createPage(title, content, null);
  }

  public Optional<String> updatePage(String pageId, String title, String content, int version) {
    if (!isConfigured()) return Optional.empty();

    try {
      Map<String, Object> body =
          Map.of(
              "version",
              Map.of("number", version + 1),
              "title",
              title,
              "type",
              "page",
              "body",
              Map.of("storage", Map.of("value", content, "representation", "storage")));

      String json = objectMapper.writeValueAsString(body);

      Request request =
          new Request.Builder()
              .url(config.getBaseUrl() + "/rest/api/content/" + pageId)
              .put(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          log.error("Failed to update Confluence page: {}", response.code());
          return Optional.empty();
        }
        JsonNode node = objectMapper.readTree(response.body().string());
        return Optional.of(node.get("id").asText());
      }
    } catch (IOException e) {
      log.error("Error updating Confluence page", e);
      return Optional.empty();
    }
  }

  public Optional<JsonNode> getPage(String pageId) {
    if (!isConfigured()) return Optional.empty();

    try {
      Request request =
          new Request.Builder()
              .url(
                  config.getBaseUrl()
                      + "/rest/api/content/"
                      + pageId
                      + "?expand=body.storage,version")
              .get()
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) return Optional.empty();
        return Optional.of(objectMapper.readTree(response.body().string()));
      }
    } catch (IOException e) {
      log.error("Error fetching Confluence page", e);
      return Optional.empty();
    }
  }

  public Optional<String> searchPages(String query) {
    if (!isConfigured()) return Optional.empty();

    try {
      String cql = String.format("space = \"%s\" AND title ~ \"%s\"", config.getSpaceKey(), query);
      HttpUrl url =
          HttpUrl.parse(config.getBaseUrl() + "/rest/api/content/search")
              .newBuilder()
              .addQueryParameter("cql", cql)
              .addQueryParameter("expand", "body.storage,version")
              .build();

      Request request = new Request.Builder().url(url).get().build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) return Optional.empty();
        return Optional.of(objectMapper.readTree(response.body().string()).toString());
      }
    } catch (IOException e) {
      log.error("Error searching Confluence pages", e);
      return Optional.empty();
    }
  }

  private String buildQuestionnaireContent(Questionnaire questionnaire, List<Question> questions) {
    StringBuilder sb = new StringBuilder();
    sb.append("<h1>").append(escapeHtml(questionnaire.getTitle())).append("</h1>");
    if (questionnaire.getDescription() != null) {
      sb.append("<p>").append(escapeHtml(questionnaire.getDescription())).append("</p>");
    }
    sb.append("<p><strong>Total Questions:</strong> ").append(questions.size()).append("</p>");
    sb.append("<hr/>");

    for (int i = 0; i < questions.size(); i++) {
      Question q = questions.get(i);
      sb.append("<h2>Q")
          .append(i + 1)
          .append(": ")
          .append(escapeHtml(q.getQuestionNumber()))
          .append("</h2>");
      sb.append("<p>").append(escapeHtml(q.getText())).append("</p>");
      sb.append("<p><em>Type: ")
          .append(q.getQuestionType())
          .append(" | Required: ")
          .append(q.getIsRequired() ? "Yes" : "No")
          .append("</em></p>");
      if (q.getOptionsJson() != null && !q.getOptionsJson().isEmpty()) {
        sb.append("<p><strong>Options:</strong></p><ul>");
        try {
          var options = objectMapper.readTree(q.getOptionsJson());
          for (JsonNode opt : options) {
            sb.append("<li>").append(escapeHtml(opt.asText())).append("</li>");
          }
        } catch (IOException ignored) {
        }
        sb.append("</ul>");
      }
      sb.append("<hr/>");
    }
    sb.append("<p><em>Generated by Quenaire Security Questionnaire Automation</em></p>");
    return sb.toString();
  }

  private String buildDocumentContent(Document document) {
    StringBuilder sb = new StringBuilder();
    sb.append("<h1>").append(escapeHtml(document.getTitle())).append("</h1>");
    if (document.getDescription() != null) {
      sb.append("<p>").append(escapeHtml(document.getDescription())).append("</p>");
    }
    sb.append("<p><strong>Source Type:</strong> ").append(document.getSourceType()).append("</p>");
    sb.append("<p><strong>File Name:</strong> ")
        .append(document.getFileName() != null ? escapeHtml(document.getFileName()) : "N/A")
        .append("</p>");
    sb.append("<p><strong>MIME Type:</strong> ")
        .append(document.getMimeType() != null ? document.getMimeType() : "N/A")
        .append("</p>");
    if (document.getPageCount() != null) {
      sb.append("<p><strong>Page Count:</strong> ").append(document.getPageCount()).append("</p>");
    }
    if (document.getWordCount() != null) {
      sb.append("<p><strong>Word Count:</strong> ").append(document.getWordCount()).append("</p>");
    }
    sb.append("<hr/>");
    sb.append("<p><em>Indexed by Quenaire Security Questionnaire Automation</em></p>");
    return sb.toString();
  }

  private String escapeHtml(String text) {
    if (text == null) return "";
    return text.replace("&", "&")
        .replace("<", "<")
        .replace(">", ">")
        .replace("\"", "\"")
        .replace("'", "'");
  }
}
