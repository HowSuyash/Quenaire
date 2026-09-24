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
package com.quenaire.ai.program.collector;

import com.quenaire.ai.program.ProgramSignalCollector;
import com.quenaire.domain.program.ProgramSignal;
import com.quenaire.integration.jira.JiraConfig;
import java.io.IOException;
import java.time.OffsetDateTime;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads operational security evidence out of Jira: whether security issues are tracked, how many
 * are open, and whether any have blown their remediation SLA.
 *
 * <p>Questionnaires routinely ask "do you track and remediate vulnerabilities on a defined
 * timeline" — this turns that from a claim into a count.
 */
@Component
public class JiraSignalCollector implements ProgramSignalCollector {

  private static final Logger log = LoggerFactory.getLogger(JiraSignalCollector.class);

  /** Issues older than this without resolution are treated as SLA breaches. */
  private static final int SLA_DAYS = 30;

  private final JiraConfig config;
  private final ObjectMapper objectMapper;
  private final OkHttpClient client;

  public JiraSignalCollector(JiraConfig config, ObjectMapper objectMapper) {
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
    return ProgramSignal.Source.JIRA;
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

    List<ProgramSignal> signals = new ArrayList<>();
    String project = config.getProjectKey();

    openSecurityIssues(project).ifPresent(signals::add);
    overdueSecurityIssues(project).ifPresent(signals::add);

    return signals;
  }

  private Optional<ProgramSignal> openSecurityIssues(String project) {
    String jql =
        String.format(
            "project = %s AND (labels in (security, vulnerability) OR issuetype = Bug) AND resolution = Unresolved",
            project);

    Optional<JsonNode> result = search(jql);
    if (result.isEmpty()) return Optional.empty();

    int total = result.get().path("total").asInt(0);

    ProgramSignal signal = new ProgramSignal();
    signal.setSource(ProgramSignal.Source.JIRA);
    signal.setExternalId("jira-open-security:" + project);
    signal.setTitle("Open security issues in " + project);
    signal.setCategory("Vulnerability Management");
    signal.setResourceType("Jira::Project");
    signal.setSourceUrl(config.getBaseUrl() + "/browse/" + project);
    signal.setObservedAt(OffsetDateTime.now());
    signal.setStatus(ProgramSignal.Status.INFORMATIONAL);
    signal.setDetail(
        String.format(
            "Security findings are tracked as Jira issues in project %s. %d issue(s) currently unresolved.",
            project, total));

    return Optional.of(signal);
  }

  private Optional<ProgramSignal> overdueSecurityIssues(String project) {
    String jql =
        String.format(
            "project = %s AND labels in (security, vulnerability) AND resolution = Unresolved AND created <= -%dd",
            project, SLA_DAYS);

    Optional<JsonNode> result = search(jql);
    if (result.isEmpty()) return Optional.empty();

    int overdue = result.get().path("total").asInt(0);

    ProgramSignal signal = new ProgramSignal();
    signal.setSource(ProgramSignal.Source.JIRA);
    signal.setExternalId("jira-sla:" + project);
    signal.setTitle("Remediation SLA adherence in " + project);
    signal.setCategory("Vulnerability Management");
    signal.setResourceType("Jira::Project");
    signal.setSourceUrl(config.getBaseUrl() + "/browse/" + project);
    signal.setObservedAt(OffsetDateTime.now());
    signal.setStatus(overdue == 0 ? ProgramSignal.Status.PASSING : ProgramSignal.Status.WARNING);
    signal.setDetail(
        String.format(
            "%d security issue(s) open longer than the %d-day remediation target.",
            overdue, SLA_DAYS));

    return Optional.of(signal);
  }

  private Optional<JsonNode> search(String jql) {
    HttpUrl url = HttpUrl.parse(config.getBaseUrl() + "/rest/api/3/search");
    if (url == null) {
      log.warn("Jira base URL is not a valid URL: {}", config.getBaseUrl());
      return Optional.empty();
    }

    HttpUrl withQuery =
        url.newBuilder()
            .addQueryParameter("jql", jql)
            // Only the total is needed; asking for zero issues keeps it cheap.
            .addQueryParameter("maxResults", "0")
            .build();

    Request request =
        new Request.Builder()
            .url(withQuery)
            .header("Authorization", Credentials.basic(config.getUsername(), config.getApiToken()))
            .header("Accept", "application/json")
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        log.debug("Jira search returned HTTP {}", response.code());
        return Optional.empty();
      }
      return Optional.of(objectMapper.readTree(response.body().string()));
    } catch (IOException | JacksonException e) {
      log.warn("Jira search failed: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
