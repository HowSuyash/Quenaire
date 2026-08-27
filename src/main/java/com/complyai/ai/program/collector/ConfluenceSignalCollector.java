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
package com.complyai.ai.program.collector;

import com.complyai.ai.program.ProgramSignalCollector;
import com.complyai.domain.program.ProgramSignal;
import com.complyai.integration.confluence.ConfluenceConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tracks the tenant's written policies in Confluence and, crucially, how fresh they are.
 *
 * <p>Auditors ask when a policy was last reviewed far more often than what it says, so each policy
 * page becomes a signal whose status reflects its age.
 */
@Component
public class ConfluenceSignalCollector implements ProgramSignalCollector {

  private static final Logger log = LoggerFactory.getLogger(ConfluenceSignalCollector.class);

  /** Most frameworks expect an annual policy review. */
  private static final int REVIEW_DUE_DAYS = 365;

  private static final int REVIEW_WARNING_DAYS = 300;
  private static final int MAX_PAGES = 50;

  /** Page titles that indicate a governing document rather than a scratch note. */
  private static final List<String> POLICY_TERMS =
      List.of("policy", "standard", "procedure", "plan", "runbook", "guideline", "charter");

  private final ConfluenceConfig config;
  private final ObjectMapper objectMapper;
  private final OkHttpClient client;

  public ConfluenceSignalCollector(ConfluenceConfig config, ObjectMapper objectMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
  }

  @Override
  public ProgramSignal.Source source() {
    return ProgramSignal.Source.CONFLUENCE;
  }

  @Override
  public boolean isConfigured() {
    return config.isConfigured();
  }

  @Override
  public List<ProgramSignal> collect() {
    if (!isConfigured()) {
      return List.of();
    }

    Optional<JsonNode> pages = listSpacePages();
    if (pages.isEmpty()) {
      return List.of();
    }

    List<ProgramSignal> signals = new ArrayList<>();

    for (JsonNode page : pages.get().path("results")) {
      String title = page.path("title").asText("");
      if (!looksLikePolicy(title)) {
        continue;
      }

      String pageId = page.path("id").asText();
      OffsetDateTime lastModified = parseWhen(page.path("version").path("when").asText(null));

      ProgramSignal signal = new ProgramSignal();
      signal.setSource(ProgramSignal.Source.CONFLUENCE);
      signal.setExternalId("confluence-page:" + pageId);
      signal.setTitle(title);
      signal.setCategory(categorize(title));
      signal.setResourceType("Confluence::Page");
      signal.setSourceUrl(
          config.getBaseUrl() + "/wiki/spaces/" + config.getSpaceKey() + "/pages/" + pageId);
      // Observation time is the page's own last-edit, not "now": that is what
      // makes freshness meaningful rather than a record of when we polled.
      signal.setObservedAt(lastModified != null ? lastModified : OffsetDateTime.now());

      if (lastModified == null) {
        signal.setStatus(ProgramSignal.Status.INFORMATIONAL);
        signal.setDetail("Policy document exists in Confluence; last review date unknown.");
      } else {
        long ageDays = java.time.Duration.between(lastModified, OffsetDateTime.now()).toDays();
        signal.setStatus(
            ageDays > REVIEW_DUE_DAYS
                ? ProgramSignal.Status.FAILING
                : ageDays > REVIEW_WARNING_DAYS
                    ? ProgramSignal.Status.WARNING
                    : ProgramSignal.Status.PASSING);
        signal.setDetail(
            String.format("Documented in Confluence and last updated %d day(s) ago.", ageDays));
      }

      signals.add(signal);
    }

    return signals;
  }

  private Optional<JsonNode> listSpacePages() {
    HttpUrl base = HttpUrl.parse(config.getBaseUrl() + "/wiki/rest/api/content");
    if (base == null) {
      log.warn("Confluence base URL is not a valid URL: {}", config.getBaseUrl());
      return Optional.empty();
    }

    HttpUrl url =
        base.newBuilder()
            .addQueryParameter("spaceKey", config.getSpaceKey())
            .addQueryParameter("type", "page")
            .addQueryParameter("limit", String.valueOf(MAX_PAGES))
            .addQueryParameter("expand", "version")
            .build();

    Request request =
        new Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(config.getUsername(), config.getApiToken()))
            .header("Accept", "application/json")
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        log.debug("Confluence content listing returned HTTP {}", response.code());
        return Optional.empty();
      }
      return Optional.of(objectMapper.readTree(response.body().string()));
    } catch (IOException e) {
      log.warn("Confluence listing failed: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private static boolean looksLikePolicy(String title) {
    String lower = title.toLowerCase();
    return POLICY_TERMS.stream().anyMatch(lower::contains);
  }

  private static String categorize(String title) {
    String lower = title.toLowerCase();
    if (lower.contains("incident")) return "Incident Response";
    if (lower.contains("access") || lower.contains("identity")) return "Access Control";
    if (lower.contains("encrypt") || lower.contains("crypto") || lower.contains("key"))
      return "Encryption";
    if (lower.contains("continuity") || lower.contains("disaster") || lower.contains("recovery"))
      return "Business Continuity";
    if (lower.contains("vendor") || lower.contains("third-party") || lower.contains("supplier"))
      return "Vendor Risk";
    if (lower.contains("privacy") || lower.contains("data protection") || lower.contains("gdpr"))
      return "Privacy";
    if (lower.contains("develop") || lower.contains("sdlc") || lower.contains("code"))
      return "Secure Development";
    return "Governance";
  }

  private static OffsetDateTime parseWhen(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
