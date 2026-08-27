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
package com.complyai.ai.program;

import com.complyai.domain.program.ControlMapping;
import com.complyai.domain.program.ControlMappingRepository;
import com.complyai.domain.program.ProgramSignal;
import com.complyai.domain.program.ProgramSignalRepository;
import com.complyai.domain.tenant.Tenant;
import com.complyai.security.TenantContext;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a live picture of what a tenant's security program actually does, and maps it onto
 * compliance frameworks.
 *
 * <p>Two jobs: pull evidence from connected systems ({@link ProgramSignalCollector}s), and answer
 * "which controls do we have evidence for" — both so questionnaire answers can cite current reality
 * rather than last year's prose.
 */
@Service
public class ProgramAwarenessService {

  private static final Logger log = LoggerFactory.getLogger(ProgramAwarenessService.class);

  /** A signal older than this stops counting as current evidence. */
  private static final int STALE_AFTER_DAYS = 30;

  /** Below this score a keyword match is noise, not a mapping. */
  private static final int MIN_MAPPING_SCORE = 40;

  private final List<ProgramSignalCollector> collectors;
  private final ProgramSignalRepository signalRepository;
  private final ControlMappingRepository mappingRepository;

  public ProgramAwarenessService(
      List<ProgramSignalCollector> collectors,
      ProgramSignalRepository signalRepository,
      ControlMappingRepository mappingRepository) {
    this.collectors = collectors;
    this.signalRepository = signalRepository;
    this.mappingRepository = mappingRepository;
  }

  // ---- Results ----------------------------------------------------------

  public record RefreshResult(
      int collected,
      int created,
      int updated,
      int mappingsCreated,
      Map<String, Integer> bySource,
      List<String> skipped) {}

  public record ControlCoverage(
      String framework,
      String controlId,
      String controlTitle,
      String category,
      boolean covered,
      String health,
      int evidenceCount,
      List<UUID> signalIds) {}

  public record CoverageSummary(
      String framework,
      int totalControls,
      int coveredControls,
      int passingControls,
      int failingControls,
      int coveragePercent,
      List<ControlCoverage> controls) {}

  public record ProgramFact(
      UUID signalId,
      String source,
      String title,
      String detail,
      String status,
      String category,
      OffsetDateTime observedAt,
      boolean stale,
      List<String> controls) {}

  // ---- Ingestion --------------------------------------------------------

  /**
   * Pull from every configured collector and reconcile against stored signals.
   *
   * <p>Re-running is safe: signals are keyed by (tenant, source, externalId), so a refresh updates
   * in place rather than accumulating duplicates.
   */
  @Transactional
  public RefreshResult refresh() {
    Tenant tenant = TenantContext.getCurrentTenant();
    if (tenant == null) {
      throw new IllegalStateException("No tenant context available");
    }
    UUID tenantId = tenant.getId();

    int collected = 0;
    int created = 0;
    int updated = 0;
    int mappingsCreated = 0;
    Map<String, Integer> bySource = new LinkedHashMap<>();
    List<String> skipped = new ArrayList<>();

    for (ProgramSignalCollector collector : collectors) {
      if (!collector.isConfigured()) {
        skipped.add(collector.source().name() + " (not configured)");
        continue;
      }

      List<ProgramSignal> incoming;
      try {
        incoming = collector.collect();
      } catch (Exception e) {
        // One broken integration must not abort the whole refresh.
        log.warn("Collector {} failed", collector.source(), e);
        skipped.add(collector.source().name() + " (failed: " + e.getMessage() + ")");
        continue;
      }

      bySource.put(collector.source().name(), incoming.size());
      collected += incoming.size();

      for (ProgramSignal signal : incoming) {
        Optional<ProgramSignal> existing =
            signalRepository.findByTenantIdAndSourceAndExternalIdAndDeletedAtIsNull(
                tenantId, signal.getSource(), signal.getExternalId());

        ProgramSignal saved;
        if (existing.isPresent()) {
          ProgramSignal target = existing.get();
          target.setTitle(signal.getTitle());
          target.setDetail(signal.getDetail());
          target.setStatus(signal.getStatus());
          target.setCategory(signal.getCategory());
          target.setResourceType(signal.getResourceType());
          target.setSourceUrl(signal.getSourceUrl());
          target.setObservedAt(signal.getObservedAt());
          saved = signalRepository.save(target);
          updated++;
        } else {
          signal.setTenant(tenant);
          saved = signalRepository.save(signal);
          created++;
        }

        mappingsCreated += mapSignalToControls(tenant, saved);
      }
    }

    log.info(
        "Program refresh for tenant {}: {} collected, {} new, {} updated, {} mappings",
        tenantId,
        collected,
        created,
        updated,
        mappingsCreated);

    return new RefreshResult(collected, created, updated, mappingsCreated, bySource, skipped);
  }

  // ---- Control mapping --------------------------------------------------

  /**
   * Map one signal onto every control it plausibly evidences.
   *
   * <p>Existing mappings a human confirmed are left alone — re-inference must never silently
   * overwrite a person's judgement.
   */
  @Transactional
  public int mapSignalToControls(Tenant tenant, ProgramSignal signal) {
    String text =
        (signal.getTitle()
                + " "
                + Optional.ofNullable(signal.getDetail()).orElse("")
                + " "
                + Optional.ofNullable(signal.getCategory()).orElse(""))
            .toLowerCase(Locale.ROOT);

    int createdCount = 0;

    for (ControlFrameworkCatalog.Control control : ControlFrameworkCatalog.all()) {
      int score = ControlFrameworkCatalog.score(control, text, signal.getCategory());
      if (score < MIN_MAPPING_SCORE) {
        continue;
      }

      Optional<ControlMapping> existing =
          mappingRepository.findByTenantIdAndSignalIdAndFrameworkAndControlIdAndDeletedAtIsNull(
              tenant.getId(), signal.getId(), control.framework(), control.id());

      if (existing.isPresent()) {
        ControlMapping mapping = existing.get();
        if (!mapping.isConfirmed()) {
          mapping.setStrength(score);
          mapping.setControlTitle(control.title());
          mappingRepository.save(mapping);
        }
        continue;
      }

      ControlMapping mapping = new ControlMapping();
      mapping.setSignalId(signal.getId());
      mapping.setFramework(control.framework());
      mapping.setControlId(control.id());
      mapping.setControlTitle(control.title());
      mapping.setStrength(score);
      mapping.setTenant(tenant);
      mappingRepository.save(mapping);
      createdCount++;
    }

    return createdCount;
  }

  /** Operator confirms an inferred mapping, pinning it against re-inference. */
  @Transactional
  public void confirmMapping(UUID mappingId, UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    mappingRepository
        .findById(mappingId)
        .filter(m -> tenantId != null && tenantId.equals(m.getTenantId()))
        .ifPresent(
            mapping -> {
              mapping.setConfirmedById(userId);
              mapping.setStrength(100);
              mappingRepository.save(mapping);
            });
  }

  @Transactional
  public void removeMapping(UUID mappingId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    mappingRepository
        .findById(mappingId)
        .filter(m -> tenantId != null && tenantId.equals(m.getTenantId()))
        .ifPresent(
            mapping -> {
              mapping.softDelete();
              mappingRepository.save(mapping);
            });
  }

  // ---- Coverage ---------------------------------------------------------

  @Transactional(readOnly = true)
  public CoverageSummary getCoverage(ControlMapping.Framework framework) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      return new CoverageSummary(framework.name(), 0, 0, 0, 0, 0, List.of());
    }

    List<ControlMapping> mappings =
        mappingRepository.findByTenantIdAndFrameworkAndDeletedAtIsNull(tenantId, framework);

    Map<UUID, ProgramSignal> signals =
        mappings.isEmpty()
            ? Map.of()
            : signalRepository
                .findByIdInAndDeletedAtIsNull(
                    mappings.stream().map(ControlMapping::getSignalId).toList())
                .stream()
                .collect(Collectors.toMap(ProgramSignal::getId, s -> s, (a, b) -> a));

    Map<String, List<ControlMapping>> byControl =
        mappings.stream().collect(Collectors.groupingBy(ControlMapping::getControlId));

    List<ControlCoverage> coverage = new ArrayList<>();
    int passing = 0;
    int failing = 0;
    int covered = 0;

    for (ControlFrameworkCatalog.Control control :
        ControlFrameworkCatalog.forFramework(framework)) {
      List<ControlMapping> forControl = byControl.getOrDefault(control.id(), List.of());

      List<ProgramSignal> evidence =
          forControl.stream()
              .map(m -> signals.get(m.getSignalId()))
              .filter(Objects::nonNull)
              // Stale evidence does not prove a control is in place today.
              .filter(s -> !s.isStale(STALE_AFTER_DAYS))
              .toList();

      String health = healthOf(evidence);
      boolean isCovered = !evidence.isEmpty();

      if (isCovered) covered++;
      if ("PASSING".equals(health)) passing++;
      if ("FAILING".equals(health)) failing++;

      coverage.add(
          new ControlCoverage(
              framework.name(),
              control.id(),
              control.title(),
              control.category(),
              isCovered,
              health,
              evidence.size(),
              evidence.stream().map(ProgramSignal::getId).toList()));
    }

    int total = coverage.size();
    int percent = total == 0 ? 0 : Math.round((covered * 100f) / total);

    return new CoverageSummary(
        framework.name(), total, covered, passing, failing, percent, coverage);
  }

  @Transactional(readOnly = true)
  public List<CoverageSummary> getAllCoverage() {
    return Arrays.stream(ControlMapping.Framework.values())
        .filter(f -> !ControlFrameworkCatalog.forFramework(f).isEmpty())
        .map(this::getCoverage)
        .toList();
  }

  /** Worst evidence wins: one failing control makes the whole control failing. */
  private static String healthOf(List<ProgramSignal> evidence) {
    if (evidence.isEmpty()) return "UNKNOWN";
    if (evidence.stream().anyMatch(s -> s.getStatus() == ProgramSignal.Status.FAILING))
      return "FAILING";
    if (evidence.stream().anyMatch(s -> s.getStatus() == ProgramSignal.Status.WARNING))
      return "WARNING";
    if (evidence.stream().anyMatch(s -> s.getStatus() == ProgramSignal.Status.PASSING))
      return "PASSING";
    return "INFORMATIONAL";
  }

  // ---- Grounding answers ------------------------------------------------

  /**
   * Facts relevant to a question, for the answer generator to cite.
   *
   * <p>This is the point of the whole subsystem: instead of asserting "we encrypt data at rest", an
   * answer can say so and point at the Config rule that proves it, dated.
   */
  @Transactional(readOnly = true)
  public List<ProgramFact> findRelevantFacts(String questionText, int limit) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null || questionText == null || questionText.isBlank()) {
      return List.of();
    }

    Set<ProgramSignal> matches = new LinkedHashSet<>();
    for (String term : significantTerms(questionText)) {
      matches.addAll(signalRepository.search(tenantId, "%" + term + "%"));
      if (matches.size() >= limit * 3) break;
    }

    List<ProgramSignal> ranked =
        matches.stream()
            // Live, healthy evidence first; stale evidence last.
            .sorted(
                Comparator.comparing((ProgramSignal s) -> s.isStale(STALE_AFTER_DAYS))
                    .thenComparing(ProgramSignal::getObservedAt, Comparator.reverseOrder()))
            .limit(limit)
            .toList();

    return ranked.stream()
        .map(
            signal -> {
              List<String> controls =
                  mappingRepository
                      .findByTenantIdAndSignalIdAndDeletedAtIsNull(tenantId, signal.getId())
                      .stream()
                      .map(m -> m.getFramework().name() + " " + m.getControlId())
                      .toList();

              return new ProgramFact(
                  signal.getId(),
                  signal.getSource().name(),
                  signal.getTitle(),
                  signal.getDetail(),
                  signal.getStatus().name(),
                  signal.getCategory(),
                  signal.getObservedAt(),
                  signal.isStale(STALE_AFTER_DAYS),
                  controls);
            })
        .toList();
  }

  /** Terms worth searching on — drops the filler that would match everything. */
  private static List<String> significantTerms(String text) {
    Set<String> stopWords =
        Set.of(
            "the",
            "and",
            "for",
            "are",
            "you",
            "your",
            "does",
            "have",
            "with",
            "what",
            "how",
            "any",
            "all",
            "was",
            "our",
            "use",
            "used",
            "please",
            "describe",
            "provide",
            "this",
            "that",
            "from",
            "has",
            "can",
            "will",
            "does",
            "there");

    return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
        .filter(w -> w.length() > 3)
        .filter(w -> !stopWords.contains(w))
        .distinct()
        .limit(8)
        .toList();
  }

  // ---- Inventory --------------------------------------------------------

  @Transactional(readOnly = true)
  public List<ProgramSignal> listSignals() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return tenantId == null
        ? List.of()
        : signalRepository.findByTenantIdAndDeletedAtIsNullOrderByObservedAtDesc(tenantId);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getProgramHealth() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      return Map.of();
    }

    Map<String, Object> health = new LinkedHashMap<>();
    health.put(
        "passing",
        signalRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, ProgramSignal.Status.PASSING));
    health.put(
        "warning",
        signalRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, ProgramSignal.Status.WARNING));
    health.put(
        "failing",
        signalRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, ProgramSignal.Status.FAILING));
    health.put(
        "informational",
        signalRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, ProgramSignal.Status.INFORMATIONAL));

    Map<String, Long> sources = new LinkedHashMap<>();
    for (ProgramSignal.Source source : ProgramSignal.Source.values()) {
      sources.put(
          source.name(),
          signalRepository.countByTenantIdAndSourceAndDeletedAtIsNull(tenantId, source));
    }
    health.put("bySource", sources);

    health.put(
        "staleCount",
        signalRepository
            .findStale(tenantId, OffsetDateTime.now().minusDays(STALE_AFTER_DAYS))
            .size());

    health.put(
        "connectors",
        collectors.stream()
            .collect(
                Collectors.toMap(
                    c -> c.source().name(),
                    ProgramSignalCollector::isConfigured,
                    (a, b) -> a,
                    LinkedHashMap::new)));

    return health;
  }
}
