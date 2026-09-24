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
import com.quenaire.integration.github.GitHubConfig;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
 * Reads engineering-side controls out of GitHub: branch protection, required reviews, Dependabot
 * alerts and code scanning.
 *
 * <p>These are the facts behind half the "secure development lifecycle" section of any
 * questionnaire, and they change often enough that a stale prose answer goes wrong quietly.
 */
@Component
public class GitHubSignalCollector implements ProgramSignalCollector {

  private static final Logger log = LoggerFactory.getLogger(GitHubSignalCollector.class);

  private final GitHubConfig config;
  private final ObjectMapper objectMapper;
  private final OkHttpClient client;

  public GitHubSignalCollector(GitHubConfig config, ObjectMapper objectMapper) {
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
    return ProgramSignal.Source.GITHUB;
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
    String repo = config.getOwner() + "/" + config.getRepository();

    collectBranchProtection(repo).ifPresent(signals::add);
    collectDependabotAlerts(repo).ifPresent(signals::add);
    collectCodeScanning(repo).ifPresent(signals::add);
    collectSecretScanning(repo).ifPresent(signals::add);

    return signals;
  }

  private Optional<ProgramSignal> collectBranchProtection(String repo) {
    // The default branch is what "protected" means to a reviewer.
    Optional<JsonNode> repoNode = get("/repos/" + repo);
    if (repoNode.isEmpty()) return Optional.empty();

    String defaultBranch = repoNode.get().path("default_branch").asText("main");
    Optional<JsonNode> protection =
        get("/repos/" + repo + "/branches/" + defaultBranch + "/protection");

    ProgramSignal signal = new ProgramSignal();
    signal.setSource(ProgramSignal.Source.GITHUB);
    signal.setExternalId("branch-protection:" + repo + ":" + defaultBranch);
    signal.setTitle("Branch protection on " + repo + "@" + defaultBranch);
    signal.setCategory("Secure Development");
    signal.setResourceType("GitHub::Branch");
    signal.setSourceUrl("https://github.com/" + repo + "/settings/branches");
    signal.setObservedAt(OffsetDateTime.now());

    if (protection.isEmpty()) {
      // 404 here means genuinely unprotected, which is the finding.
      signal.setStatus(ProgramSignal.Status.FAILING);
      signal.setDetail("The default branch has no protection rules configured.");
      return Optional.of(signal);
    }

    JsonNode node = protection.get();
    int requiredReviews =
        node.path("required_pull_request_reviews").path("required_approving_review_count").asInt(0);
    boolean requiresStatusChecks = node.path("required_status_checks").isObject();
    boolean enforcesAdmins = node.path("enforce_admins").path("enabled").asBoolean(false);

    signal.setStatus(
        requiredReviews >= 1 ? ProgramSignal.Status.PASSING : ProgramSignal.Status.WARNING);
    signal.setDetail(
        String.format(
            "Default branch requires %d approving review(s); status checks %s; admin enforcement %s.",
            requiredReviews,
            requiresStatusChecks ? "required" : "not required",
            enforcesAdmins ? "on" : "off"));

    return Optional.of(signal);
  }

  private Optional<ProgramSignal> collectDependabotAlerts(String repo) {
    Optional<JsonNode> alerts =
        get("/repos/" + repo + "/dependabot/alerts?state=open&per_page=100");
    if (alerts.isEmpty()) return Optional.empty();

    int critical = 0;
    int high = 0;
    int total = 0;

    for (JsonNode alert : alerts.get()) {
      total++;
      String severity = alert.path("security_advisory").path("severity").asText("");
      if ("critical".equalsIgnoreCase(severity)) critical++;
      else if ("high".equalsIgnoreCase(severity)) high++;
    }

    ProgramSignal signal = new ProgramSignal();
    signal.setSource(ProgramSignal.Source.GITHUB);
    signal.setExternalId("dependabot:" + repo);
    signal.setTitle("Dependency vulnerability alerts on " + repo);
    signal.setCategory("Vulnerability Management");
    signal.setResourceType("GitHub::Dependabot");
    signal.setSourceUrl("https://github.com/" + repo + "/security/dependabot");
    signal.setObservedAt(OffsetDateTime.now());
    signal.setStatus(
        critical > 0
            ? ProgramSignal.Status.FAILING
            : high > 0 ? ProgramSignal.Status.WARNING : ProgramSignal.Status.PASSING);
    signal.setDetail(
        String.format(
            "%d open alerts (%d critical, %d high). Dependabot scanning is enabled.",
            total, critical, high));

    return Optional.of(signal);
  }

  private Optional<ProgramSignal> collectCodeScanning(String repo) {
    Optional<JsonNode> alerts =
        get("/repos/" + repo + "/code-scanning/alerts?state=open&per_page=100");
    if (alerts.isEmpty()) return Optional.empty();

    int open = alerts.get().size();

    ProgramSignal signal = new ProgramSignal();
    signal.setSource(ProgramSignal.Source.GITHUB);
    signal.setExternalId("code-scanning:" + repo);
    signal.setTitle("Static analysis (code scanning) on " + repo);
    signal.setCategory("Secure Development");
    signal.setResourceType("GitHub::CodeScanning");
    signal.setSourceUrl("https://github.com/" + repo + "/security/code-scanning");
    signal.setObservedAt(OffsetDateTime.now());
    signal.setStatus(open == 0 ? ProgramSignal.Status.PASSING : ProgramSignal.Status.WARNING);
    signal.setDetail("Code scanning is enabled with " + open + " open alert(s).");

    return Optional.of(signal);
  }

  private Optional<ProgramSignal> collectSecretScanning(String repo) {
    Optional<JsonNode> alerts =
        get("/repos/" + repo + "/secret-scanning/alerts?state=open&per_page=100");
    if (alerts.isEmpty()) return Optional.empty();

    int open = alerts.get().size();

    ProgramSignal signal = new ProgramSignal();
    signal.setSource(ProgramSignal.Source.GITHUB);
    signal.setExternalId("secret-scanning:" + repo);
    signal.setTitle("Secret scanning on " + repo);
    signal.setCategory("Secure Development");
    signal.setResourceType("GitHub::SecretScanning");
    signal.setSourceUrl("https://github.com/" + repo + "/security/secret-scanning");
    signal.setObservedAt(OffsetDateTime.now());
    signal.setStatus(open == 0 ? ProgramSignal.Status.PASSING : ProgramSignal.Status.FAILING);
    signal.setDetail("Secret scanning is enabled with " + open + " unresolved alert(s).");

    return Optional.of(signal);
  }

  /**
   * GET a GitHub path, returning empty for any non-2xx.
   *
   * <p>404 is expected and meaningful here (feature not enabled, or the token lacks the scope), so
   * callers decide what its absence implies rather than treating it as an error.
   */
  private Optional<JsonNode> get(String path) {
    Request request =
        new Request.Builder()
            .url(config.getBaseUrl() + path)
            .header("Authorization", "Bearer " + config.getToken())
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        log.debug("GitHub {} returned HTTP {}", path, response.code());
        return Optional.empty();
      }
      return Optional.of(objectMapper.readTree(response.body().string()));
    } catch (IOException | JacksonException e) {
      log.warn("GitHub request failed for {}: {}", path, e.getMessage());
      return Optional.empty();
    }
  }
}
