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
package com.complyai.integration.slack;

import com.complyai.domain.document.Document;
import com.complyai.domain.questionnaire.Question;
import com.complyai.domain.questionnaire.Questionnaire;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SlackIntegrationService {

  private static final Logger log = LoggerFactory.getLogger(SlackIntegrationService.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final SlackConfig config;
  private final OkHttpClient client;
  private final ObjectMapper objectMapper;

  public SlackIntegrationService(SlackConfig config, ObjectMapper objectMapper) {
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

  public Optional<String> sendMessage(String channel, String text) {
    if (!isConfigured()) {
      log.warn("Slack integration not configured");
      return Optional.empty();
    }

    try {
      Map<String, Object> payload =
          Map.of("channel", channel != null ? channel : config.getDefaultChannel(), "text", text);

      String json = objectMapper.writeValueAsString(payload);

      Request request =
          new Request.Builder()
              .url("https://slack.com/api/chat.postMessage")
              .header("Authorization", "Bearer " + config.getBotToken())
              .header("Content-Type", "application/json")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          log.error("Failed to send Slack message: {}", response.code());
          return Optional.empty();
        }
        JsonNode node = objectMapper.readTree(response.body().string());
        if (node.get("ok").asBoolean()) {
          return Optional.of(node.get("ts").asText());
        }
        log.error("Slack API error: {}", node.get("error").asText());
        return Optional.empty();
      }
    } catch (IOException e) {
      log.error("Error sending Slack message", e);
      return Optional.empty();
    }
  }

  public Optional<String> sendMessageWithBlocks(
      String channel, String text, List<Map<String, Object>> blocks) {
    if (!isConfigured()) {
      log.warn("Slack integration not configured");
      return Optional.empty();
    }

    try {
      Map<String, Object> payload =
          Map.of(
              "channel", channel != null ? channel : config.getDefaultChannel(),
              "text", text,
              "blocks", blocks);

      String json = objectMapper.writeValueAsString(payload);

      Request request =
          new Request.Builder()
              .url("https://slack.com/api/chat.postMessage")
              .header("Authorization", "Bearer " + config.getBotToken())
              .header("Content-Type", "application/json")
              .post(RequestBody.create(json, JSON))
              .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          log.error("Failed to send Slack message: {}", response.code());
          return Optional.empty();
        }
        JsonNode node = objectMapper.readTree(response.body().string());
        if (node.get("ok").asBoolean()) {
          return Optional.of(node.get("ts").asText());
        }
        log.error("Slack API error: {}", node.get("error").asText());
        return Optional.empty();
      }
    } catch (IOException e) {
      log.error("Error sending Slack message with blocks", e);
      return Optional.empty();
    }
  }

  public Optional<String> sendQuestionnaireNotification(
      Questionnaire questionnaire, String channel) {
    String text =
        String.format(":clipboard: New security questionnaire: *%s*", questionnaire.getTitle());

    List<Map<String, Object>> blocks =
        List.of(
            Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text)),
            Map.of(
                "type",
                "section",
                "fields",
                List.of(
                    Map.of(
                        "type",
                        "mrkdwn",
                        "text",
                        "*Total Questions:*\n" + questionnaire.getTotalQuestions()),
                    Map.of(
                        "type", "mrkdwn", "text", "*Status:*\n" + questionnaire.getStatus().name()),
                    Map.of(
                        "type",
                        "mrkdwn",
                        "text",
                        "*Due Date:*\n"
                            + (questionnaire.getDueDate() != null
                                ? questionnaire.getDueDate().toString()
                                : "Not set")))),
            Map.of(
                "type",
                "context",
                "elements",
                List.of(
                    Map.of(
                        "type", "mrkdwn", "text", "ComplyAI Security Questionnaire Automation"))));

    return sendMessageWithBlocks(channel, text, blocks);
  }

  public Optional<String> sendQuestionApprovalRequest(
      Question question, String answer, String channel) {
    String text =
        String.format(":question: Question requires review: *Q%s*", question.getQuestionNumber());

    List<Map<String, Object>> blocks =
        List.of(
            Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text)),
            Map.of(
                "type",
                "section",
                "fields",
                List.of(
                    Map.of(
                        "type",
                        "mrkdwn",
                        "text",
                        "*Question:*\n" + truncate(question.getText(), 200)),
                    Map.of(
                        "type", "mrkdwn", "text", "*Type:*\n" + question.getQuestionType().name()),
                    Map.of("type", "mrkdwn", "text", "*AI Answer:*\n" + truncate(answer, 200)))),
            Map.of(
                "type",
                "actions",
                "elements",
                List.of(
                    Map.of(
                        "type", "button",
                        "text", Map.of("type", "plain_text", "text", "Approve"),
                        "style", "primary",
                        "value", "approve_" + question.getId(),
                        "action_id", "approve_answer"),
                    Map.of(
                        "type", "button",
                        "text", Map.of("type", "plain_text", "text", "Reject"),
                        "style", "danger",
                        "value", "reject_" + question.getId(),
                        "action_id", "reject_answer"),
                    Map.of(
                        "type",
                        "button",
                        "text",
                        Map.of("type", "plain_text", "text", "Edit"),
                        "value",
                        "edit_" + question.getId(),
                        "action_id",
                        "edit_answer"))),
            Map.of(
                "type",
                "context",
                "elements",
                List.of(
                    Map.of(
                        "type", "mrkdwn", "text", "ComplyAI Security Questionnaire Automation"))));

    return sendMessageWithBlocks(channel, text, blocks);
  }

  public Optional<String> sendQuestionnaireCompleteNotification(
      Questionnaire questionnaire, int approvedCount, int totalCount, String channel) {
    String text =
        String.format(
            ":white_check_mark: Questionnaire *%s* review complete (%d/%d approved)",
            questionnaire.getTitle(), approvedCount, totalCount);

    List<Map<String, Object>> blocks =
        List.of(
            Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text)),
            Map.of(
                "type",
                "section",
                "fields",
                List.of(
                    Map.of("type", "mrkdwn", "text", "*Approved:*\n" + approvedCount),
                    Map.of("type", "mrkdwn", "text", "*Total:*\n" + totalCount),
                    Map.of(
                        "type",
                        "mrkdwn",
                        "text",
                        "*Status:*\n"
                            + (approvedCount == totalCount
                                ? "Ready for Export"
                                : "Needs Attention")))),
            Map.of(
                "type",
                "context",
                "elements",
                List.of(
                    Map.of(
                        "type", "mrkdwn", "text", "ComplyAI Security Questionnaire Automation"))));

    return sendMessageWithBlocks(channel, text, blocks);
  }

  public Optional<String> sendDocumentIndexedNotification(Document document, String channel) {
    String text = String.format(":page_facing_up: Document indexed: *%s*", document.getTitle());

    List<Map<String, Object>> blocks =
        List.of(
            Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text)),
            Map.of(
                "type",
                "section",
                "fields",
                List.of(
                    Map.of("type", "mrkdwn", "text", "*Source Type:*\n" + document.getSourceType()),
                    Map.of(
                        "type",
                        "mrkdwn",
                        "text",
                        "*File Name:*\n"
                            + (document.getFileName() != null ? document.getFileName() : "N/A")),
                    Map.of(
                        "type",
                        "mrkdwn",
                        "text",
                        "*Pages:*\n"
                            + (document.getPageCount() != null
                                ? String.valueOf(document.getPageCount())
                                : "N/A")),
                    Map.of(
                        "type",
                        "mrkdwn",
                        "text",
                        "*Words:*\n"
                            + (document.getWordCount() != null
                                ? String.valueOf(document.getWordCount())
                                : "N/A")))),
            Map.of(
                "type",
                "context",
                "elements",
                List.of(
                    Map.of(
                        "type", "mrkdwn", "text", "ComplyAI Security Questionnaire Automation"))));

    return sendMessageWithBlocks(channel, text, blocks);
  }

  public Optional<String> sendAlert(String title, String message, String level, String channel) {
    String color =
        switch (level.toUpperCase()) {
          case "CRITICAL", "ERROR" -> "#ff0000";
          case "WARNING" -> "#ff9900";
          case "INFO" -> "#2196f3";
          default -> "#36a64f";
        };

    String emoji =
        switch (level.toUpperCase()) {
          case "CRITICAL" -> ":rotating_light:";
          case "ERROR" -> ":x:";
          case "WARNING" -> ":warning:";
          case "INFO" -> ":information_source:";
          default -> ":white_check_mark:";
        };

    String text = String.format("%s *%s*", emoji, title);

    List<Map<String, Object>> blocks =
        List.of(
            Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text)),
            Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", message)),
            Map.of(
                "type",
                "context",
                "elements",
                List.of(
                    Map.of(
                        "type", "mrkdwn", "text", "ComplyAI Security Questionnaire Automation"))));

    return sendMessageWithBlocks(channel, text, blocks);
  }

  public CompletableFuture<Optional<String>> sendMessageAsync(String channel, String text) {
    return CompletableFuture.supplyAsync(() -> sendMessage(channel, text));
  }

  private String truncate(String text, int maxLength) {
    if (text == null) return "";
    return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
  }
}
