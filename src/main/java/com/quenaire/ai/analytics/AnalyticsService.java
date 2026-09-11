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
package com.quenaire.ai.analytics;

import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionRepository;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.quenaire.domain.sme.SMEAssignment;
import com.quenaire.domain.sme.SMEAssignmentRepository;
import com.quenaire.domain.sme.SMEAssignmentStatus;
import com.quenaire.security.TenantContext;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers the question every buyer asks in month three: "what did this actually save us?"
 *
 * <p>Savings are modelled, not measured — the baseline minutes-per-question and hourly rate are
 * configuration, and every figure is reported alongside the assumptions that produced it so nobody
 * mistakes a model for a measurement.
 */
@Service
public class AnalyticsService {

  private final VantaQuestionnaireRepository questionnaireRepository;
  private final VantaQuestionRepository questionRepository;
  private final SMEAssignmentRepository assignmentRepository;

  /** Minutes a security analyst spends answering one question by hand. */
  private final double manualMinutesPerQuestion;

  /** Minutes still spent per question when reviewing an AI draft. */
  private final double reviewMinutesPerQuestion;

  /** Fully-loaded hourly cost of the person doing that work. */
  private final double hourlyRate;

  private final String currency;

  public AnalyticsService(
      VantaQuestionnaireRepository questionnaireRepository,
      VantaQuestionRepository questionRepository,
      SMEAssignmentRepository assignmentRepository,
      @Value("${analytics.manual-minutes-per-question:20}") double manualMinutesPerQuestion,
      @Value("${analytics.review-minutes-per-question:3}") double reviewMinutesPerQuestion,
      @Value("${analytics.hourly-rate:95}") double hourlyRate,
      @Value("${analytics.currency:USD}") String currency) {
    this.questionnaireRepository = questionnaireRepository;
    this.questionRepository = questionRepository;
    this.assignmentRepository = assignmentRepository;
    this.manualMinutesPerQuestion = manualMinutesPerQuestion;
    this.reviewMinutesPerQuestion = reviewMinutesPerQuestion;
    this.hourlyRate = hourlyRate;
    this.currency = currency;
  }

  // ---- Shapes -----------------------------------------------------------

  public record Totals(
      long questionnaires,
      long questionnairesCompleted,
      long questions,
      long questionsAnswered,
      long questionsApproved,
      long questionsAutoApproved,
      long questionsHumanEdited,
      double automationRate,
      double approvalRate) {}

  public record RoiAssumptions(
      double manualMinutesPerQuestion,
      double reviewMinutesPerQuestion,
      double hourlyRate,
      String currency) {}

  public record Roi(
      double manualHours,
      double actualHours,
      double hoursSaved,
      double costAvoided,
      double savingsPercent,
      RoiAssumptions assumptions) {}

  public record TurnaroundStats(
      Double medianHours,
      Double averageHours,
      Double fastestHours,
      Double slowestHours,
      long sampleSize) {}

  public record TrendPoint(
      LocalDate date, long questionsAnswered, long questionsApproved, double hoursSaved) {}

  public record CategoryBreakdown(
      String category, long total, long approved, double averageConfidence) {}

  public record ConfidenceBand(String band, long count) {}

  public record SmeWorkload(
      UUID smeUserId,
      long assigned,
      long completed,
      long open,
      long overdue,
      Double averageHoursToComplete) {}

  public record Dashboard(
      Totals totals,
      Roi roi,
      TurnaroundStats turnaround,
      List<TrendPoint> trend,
      List<CategoryBreakdown> categories,
      List<ConfidenceBand> confidence,
      List<SmeWorkload> smeWorkload) {}

  // ---- Entry point ------------------------------------------------------

  @Transactional(readOnly = true)
  public Dashboard getDashboard(int days) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      return emptyDashboard();
    }

    OffsetDateTime since = OffsetDateTime.now().minusDays(Math.max(days, 1));

    List<VantaQuestionnaire> questionnaires =
        questionnaireRepository.findAll().stream()
            .filter(q -> tenantId.equals(q.getTenantId()))
            .filter(q -> q.getDeletedAt() == null)
            .toList();

    List<VantaQuestion> questions =
        questionnaires.stream()
            .flatMap(
                q ->
                    questionRepository
                        .findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(q.getId())
                        .stream())
            .toList();

    Totals totals = computeTotals(questionnaires, questions);
    Roi roi = computeRoi(totals);

    return new Dashboard(
        totals,
        roi,
        computeTurnaround(questionnaires),
        computeTrend(questions, since),
        computeCategories(questions),
        computeConfidenceBands(questions),
        computeSmeWorkload(tenantId));
  }

  // ---- Totals -----------------------------------------------------------

  private Totals computeTotals(
      List<VantaQuestionnaire> questionnaires, List<VantaQuestion> questions) {
    long completed =
        questionnaires.stream()
            .filter(
                q ->
                    q.getStatus() == VantaQuestionnaire.QuestionnaireStatus.APPROVED
                        || q.getStatus() == VantaQuestionnaire.QuestionnaireStatus.SUBMITTED
                        || q.getStatus() == VantaQuestionnaire.QuestionnaireStatus.EXPORTED)
            .count();

    long answered =
        questions.stream()
            .filter(q -> q.getFinalAnswer() != null && !q.getFinalAnswer().isBlank())
            .count();

    long approved =
        questions.stream()
            .filter(q -> q.getStatus() == VantaQuestion.QuestionStatus.APPROVED)
            .count();

    // "Auto-approved" means the human never rewrote the draft — the AI answer
    // survived review intact. Editing it is a partial, not a full, saving.
    long humanEdited =
        questions.stream()
            .filter(q -> q.getHumanAnswer() != null && !q.getHumanAnswer().isBlank())
            .count();
    long autoApproved = Math.max(approved - humanEdited, 0);

    double automationRate = answered == 0 ? 0 : round2((autoApproved * 100.0) / answered);
    double approvalRate = answered == 0 ? 0 : round2((approved * 100.0) / answered);

    return new Totals(
        questionnaires.size(),
        completed,
        questions.size(),
        answered,
        approved,
        autoApproved,
        humanEdited,
        automationRate,
        approvalRate);
  }

  // ---- ROI --------------------------------------------------------------

  private Roi computeRoi(Totals totals) {
    double manualHours = (totals.questionsAnswered() * manualMinutesPerQuestion) / 60.0;

    // Untouched drafts still cost a review; edited ones cost review plus a
    // share of the manual effort, so savings never look free.
    double reviewHours = (totals.questionsAutoApproved() * reviewMinutesPerQuestion) / 60.0;
    double editedHours =
        (totals.questionsHumanEdited()
                * (reviewMinutesPerQuestion + manualMinutesPerQuestion * 0.5))
            / 60.0;
    double actualHours = reviewHours + editedHours;

    double hoursSaved = Math.max(manualHours - actualHours, 0);
    double costAvoided = hoursSaved * hourlyRate;
    double savingsPercent = manualHours == 0 ? 0 : round2((hoursSaved / manualHours) * 100.0);

    return new Roi(
        round2(manualHours),
        round2(actualHours),
        round2(hoursSaved),
        round2(costAvoided),
        savingsPercent,
        new RoiAssumptions(
            manualMinutesPerQuestion, reviewMinutesPerQuestion, hourlyRate, currency));
  }

  // ---- Turnaround -------------------------------------------------------

  private TurnaroundStats computeTurnaround(List<VantaQuestionnaire> questionnaires) {
    List<Double> hours =
        questionnaires.stream()
            .filter(q -> q.getCompletedAt() != null && q.getCreatedAt() != null)
            .map(
                q ->
                    Duration.between(q.getCreatedAt(), q.getCompletedAt().toInstant()).toMinutes()
                        / 60.0)
            .filter(h -> h >= 0)
            .sorted()
            .toList();

    if (hours.isEmpty()) {
      return new TurnaroundStats(null, null, null, null, 0);
    }

    double median =
        hours.size() % 2 == 1
            ? hours.get(hours.size() / 2)
            : (hours.get(hours.size() / 2 - 1) + hours.get(hours.size() / 2)) / 2.0;

    double average = hours.stream().mapToDouble(Double::doubleValue).average().orElse(0);

    return new TurnaroundStats(
        round2(median),
        round2(average),
        round2(hours.get(0)),
        round2(hours.get(hours.size() - 1)),
        hours.size());
  }

  // ---- Trend ------------------------------------------------------------

  private List<TrendPoint> computeTrend(List<VantaQuestion> questions, OffsetDateTime since) {
    Map<LocalDate, long[]> byDay = new TreeMap<>();

    for (VantaQuestion question : questions) {
      OffsetDateTime when =
          question.getReviewedAt() != null
              ? question.getReviewedAt()
              : question.getCreatedAt() != null
                  ? question.getCreatedAt().atOffset(ZoneOffset.UTC)
                  : null;

      if (when == null || when.isBefore(since)) continue;

      LocalDate day = when.toLocalDate();
      long[] counts = byDay.computeIfAbsent(day, d -> new long[2]);

      if (question.getFinalAnswer() != null && !question.getFinalAnswer().isBlank()) counts[0]++;
      if (question.getStatus() == VantaQuestion.QuestionStatus.APPROVED) counts[1]++;
    }

    // Fill gaps so a chart shows real dips instead of interpolating across them.
    List<TrendPoint> trend = new ArrayList<>();
    LocalDate cursor = since.toLocalDate();
    LocalDate today = LocalDate.now();

    while (!cursor.isAfter(today)) {
      long[] counts = byDay.getOrDefault(cursor, new long[2]);
      double savedHours =
          (counts[0] * (manualMinutesPerQuestion - reviewMinutesPerQuestion)) / 60.0;
      trend.add(new TrendPoint(cursor, counts[0], counts[1], round2(Math.max(savedHours, 0))));
      cursor = cursor.plusDays(1);
    }

    return trend;
  }

  // ---- Breakdowns -------------------------------------------------------

  private List<CategoryBreakdown> computeCategories(List<VantaQuestion> questions) {
    Map<String, List<VantaQuestion>> byCategory =
        questions.stream()
            .collect(
                Collectors.groupingBy(
                    q -> {
                      if (q.getCategory() != null && !q.getCategory().isBlank())
                        return q.getCategory();
                      if (q.getSection() != null && !q.getSection().isBlank())
                        return q.getSection();
                      return "Uncategorized";
                    }));

    return byCategory.entrySet().stream()
        .map(
            entry -> {
              List<VantaQuestion> group = entry.getValue();
              long approved =
                  group.stream()
                      .filter(q -> q.getStatus() == VantaQuestion.QuestionStatus.APPROVED)
                      .count();
              double avgConfidence =
                  group.stream()
                      .filter(q -> q.getConfidenceScore() != null)
                      .mapToInt(VantaQuestion::getConfidenceScore)
                      .average()
                      .orElse(0);
              return new CategoryBreakdown(
                  entry.getKey(), group.size(), approved, round2(avgConfidence));
            })
        .sorted(Comparator.comparingLong(CategoryBreakdown::total).reversed())
        .limit(12)
        .toList();
  }

  private List<ConfidenceBand> computeConfidenceBands(List<VantaQuestion> questions) {
    long high = questions.stream().filter(q -> score(q) >= 90).count();
    long medium = questions.stream().filter(q -> score(q) >= 70 && score(q) < 90).count();
    long low =
        questions.stream().filter(q -> q.getConfidenceScore() != null && score(q) < 70).count();
    long unscored = questions.stream().filter(q -> q.getConfidenceScore() == null).count();

    return List.of(
        new ConfidenceBand("High (90+)", high),
        new ConfidenceBand("Medium (70-89)", medium),
        new ConfidenceBand("Low (<70)", low),
        new ConfidenceBand("Not scored", unscored));
  }

  private static int score(VantaQuestion question) {
    return question.getConfidenceScore() != null ? question.getConfidenceScore() : -1;
  }

  private List<SmeWorkload> computeSmeWorkload(UUID tenantId) {
    List<SMEAssignment> assignments =
        assignmentRepository.findByTenantIdAndDeletedAtIsNullOrderByDueAtAsc(tenantId);

    Map<UUID, List<SMEAssignment>> bySme =
        assignments.stream().collect(Collectors.groupingBy(SMEAssignment::getSmeUserId));

    return bySme.entrySet().stream()
        .map(
            entry -> {
              List<SMEAssignment> group = entry.getValue();

              long completed =
                  group.stream()
                      .filter(a -> a.getStatus() == SMEAssignmentStatus.COMPLETED)
                      .count();
              long open = group.stream().filter(SMEAssignment::isOpen).count();
              long overdue = group.stream().filter(SMEAssignment::isOverdue).count();

              Double avgHours =
                  group.stream()
                      .filter(a -> a.getCompletedAt() != null && a.getAssignedAt() != null)
                      .mapToDouble(
                          a ->
                              Duration.between(a.getAssignedAt(), a.getCompletedAt()).toMinutes()
                                  / 60.0)
                      .average()
                      .stream()
                      .boxed()
                      .findFirst()
                      .orElse(null);

              return new SmeWorkload(
                  entry.getKey(),
                  group.size(),
                  completed,
                  open,
                  overdue,
                  avgHours != null ? round2(avgHours) : null);
            })
        .sorted(Comparator.comparingLong(SmeWorkload::open).reversed())
        .toList();
  }

  // ---- Helpers ----------------------------------------------------------

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private Dashboard emptyDashboard() {
    return new Dashboard(
        new Totals(0, 0, 0, 0, 0, 0, 0, 0, 0),
        new Roi(
            0,
            0,
            0,
            0,
            0,
            new RoiAssumptions(
                manualMinutesPerQuestion, reviewMinutesPerQuestion, hourlyRate, currency)),
        new TurnaroundStats(null, null, null, null, 0),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
