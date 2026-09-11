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
package com.quenaire.ai.agent;

import com.quenaire.domain.questionnaire.vanta.Citation;
import com.quenaire.domain.questionnaire.vanta.CitationRepository;
import com.quenaire.domain.questionnaire.vanta.ConfidenceLevel;
import com.quenaire.domain.questionnaire.vanta.VantaQuestion;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionRepository;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaire;
import com.quenaire.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.quenaire.domain.sme.SMEAssignment;
import com.quenaire.domain.sme.SMEAssignmentRepository;
import com.quenaire.domain.sme.SMEAssignmentStatus;
import com.quenaire.security.TenantContext;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the SME review queue: every question that still needs a human decision, joined with its
 * questionnaire, SME assignment and citations.
 *
 * <p>Kept separate from {@link SMERoutingService} (which decides <em>who</em> reviews) so the read
 * model can evolve without touching routing.
 */
@Service
public class ReviewQueueService {

  /** Question states that still require a human decision. */
  private static final Set<VantaQuestion.QuestionStatus> REVIEWABLE =
      EnumSet.of(
          VantaQuestion.QuestionStatus.AI_DRAFTED,
          VantaQuestion.QuestionStatus.NEEDS_REVIEW,
          VantaQuestion.QuestionStatus.DELEGATED,
          VantaQuestion.QuestionStatus.HUMAN_EDITED);

  private final VantaQuestionRepository questionRepository;
  private final VantaQuestionnaireRepository questionnaireRepository;
  private final CitationRepository citationRepository;
  private final SMEAssignmentRepository assignmentRepository;
  private final SMERoutingService smeRoutingService;

  public ReviewQueueService(
      VantaQuestionRepository questionRepository,
      VantaQuestionnaireRepository questionnaireRepository,
      CitationRepository citationRepository,
      SMEAssignmentRepository assignmentRepository,
      SMERoutingService smeRoutingService) {
    this.questionRepository = questionRepository;
    this.questionnaireRepository = questionnaireRepository;
    this.citationRepository = citationRepository;
    this.assignmentRepository = assignmentRepository;
    this.smeRoutingService = smeRoutingService;
  }

  // ---- Read model -------------------------------------------------------

  public record CitationView(
      UUID id,
      UUID documentId,
      String documentTitle,
      UUID chunkId,
      String snippet,
      Double relevanceScore,
      Integer pageNumber,
      String sectionTitle) {}

  public record QueueItem(
      UUID questionId,
      UUID questionnaireId,
      String questionnaireTitle,
      String questionNumber,
      String questionText,
      String category,
      String section,
      String questionType,
      List<String> options,
      String aiAnswer,
      String finalAnswer,
      Integer confidenceScore,
      String confidenceLevel,
      String questionStatus,
      String reviewStatus,
      OffsetDateTime assignedAt,
      OffsetDateTime dueAt,
      boolean overdue,
      SMERoutingService.SMEProfile sme,
      List<CitationView> citations,
      String reviewNotes,
      boolean stalenessDetected,
      OffsetDateTime updatedAt) {}

  public record QueueStats(
      long total,
      long pending,
      long inProgress,
      long approved,
      long rejected,
      long overdue,
      long highConfidence,
      long activeSMEs) {}

  public record QueueResponse(List<QueueItem> items, QueueStats stats) {}

  public record QueueFilter(
      UUID questionnaireId,
      UUID smeUserId,
      String status,
      String confidence,
      String search,
      boolean overdueOnly) {}

  // ---- Queue assembly ---------------------------------------------------

  @Transactional(readOnly = true)
  public QueueResponse getQueue(QueueFilter filter) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      return new QueueResponse(List.of(), new QueueStats(0, 0, 0, 0, 0, 0, 0, 0));
    }

    List<VantaQuestion> questions = loadCandidateQuestions(tenantId, filter);

    // Assignments for these questions, in one round-trip.
    Map<UUID, SMEAssignment> assignments =
        questions.isEmpty()
            ? Map.of()
            : assignmentRepository
                .findByTenantIdAndQuestionIdInAndDeletedAtIsNull(
                    tenantId, questions.stream().map(VantaQuestion::getId).toList())
                .stream()
                // Newest assignment wins if a question was re-routed over time.
                .collect(
                    Collectors.toMap(
                        SMEAssignment::getQuestionId,
                        a -> a,
                        (a, b) -> a.getAssignedAt().isAfter(b.getAssignedAt()) ? a : b));

    Map<UUID, SMERoutingService.SMEProfile> smeProfiles =
        smeRoutingService.getProfilesByUserIds(
            assignments.values().stream()
                .map(SMEAssignment::getSmeUserId)
                .collect(Collectors.toSet()));

    Map<UUID, String> questionnaireTitles = loadQuestionnaireTitles(questions);
    Map<UUID, Citation> citations = loadCitations(questions);

    List<QueueItem> items = new ArrayList<>();
    for (VantaQuestion question : questions) {
      SMEAssignment assignment = assignments.get(question.getId());

      if (filter != null
          && filter.smeUserId() != null
          && (assignment == null || !filter.smeUserId().equals(assignment.getSmeUserId()))) {
        continue;
      }
      if (filter != null
          && filter.overdueOnly()
          && (assignment == null || !assignment.isOverdue())) {
        continue;
      }

      items.add(toQueueItem(question, assignment, smeProfiles, questionnaireTitles, citations));
    }

    items = applyItemFilters(items, filter);
    items.sort(queueOrdering());

    return new QueueResponse(items, buildStats(tenantId, items));
  }

  private List<VantaQuestion> loadCandidateQuestions(UUID tenantId, QueueFilter filter) {
    if (filter != null && filter.questionnaireId() != null) {
      return questionRepository
          .findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(filter.questionnaireId())
          .stream()
          .filter(q -> tenantId.equals(q.getTenantId()))
          .filter(q -> REVIEWABLE.contains(q.getStatus()))
          .toList();
    }

    List<VantaQuestion> all = new ArrayList<>();
    for (VantaQuestion.QuestionStatus status : REVIEWABLE) {
      all.addAll(questionRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, status));
    }
    return all;
  }

  private Map<UUID, String> loadQuestionnaireTitles(List<VantaQuestion> questions) {
    Set<UUID> ids =
        questions.stream()
            .map(VantaQuestion::getQuestionnaireId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    return questionnaireRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(VantaQuestionnaire::getId, VantaQuestionnaire::getTitle, (a, b) -> a));
  }

  private Map<UUID, Citation> loadCitations(List<VantaQuestion> questions) {
    Set<UUID> citationIds =
        questions.stream()
            .flatMap(q -> q.getCitationIds().stream())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (citationIds.isEmpty()) {
      return Map.of();
    }
    return citationRepository.findAllById(citationIds).stream()
        .collect(Collectors.toMap(Citation::getId, c -> c, (a, b) -> a));
  }

  private QueueItem toQueueItem(
      VantaQuestion question,
      SMEAssignment assignment,
      Map<UUID, SMERoutingService.SMEProfile> smeProfiles,
      Map<UUID, String> questionnaireTitles,
      Map<UUID, Citation> citations) {
    Integer score = question.getConfidenceScore();
    ConfidenceLevel level = ConfidenceLevel.fromScore(score != null ? score : 0);

    List<CitationView> citationViews =
        question.getCitationIds().stream()
            .map(citations::get)
            .filter(Objects::nonNull)
            .map(
                c ->
                    new CitationView(
                        c.getId(),
                        c.getDocumentId(),
                        c.getDocumentTitle(),
                        c.getChunkId(),
                        c.getExactQuote(),
                        c.getRelevanceScore(),
                        c.getPageNumber(),
                        c.getSectionTitle()))
            .toList();

    return new QueueItem(
        question.getId(),
        question.getQuestionnaireId(),
        questionnaireTitles.getOrDefault(question.getQuestionnaireId(), "Untitled questionnaire"),
        question.getQuestionNumber(),
        question.getText(),
        question.getCategory(),
        question.getSection(),
        question.getQuestionType().name(),
        question.getOptions(),
        question.getAiAnswer(),
        question.getFinalAnswer(),
        score,
        level.name(),
        question.getStatus().name(),
        assignment != null ? assignment.getStatus().name() : SMEAssignmentStatus.PENDING.name(),
        assignment != null ? assignment.getAssignedAt() : null,
        assignment != null ? assignment.getDueAt() : null,
        assignment != null && assignment.isOverdue(),
        assignment != null ? smeProfiles.get(assignment.getSmeUserId()) : null,
        citationViews,
        question.getReviewNotes(),
        Boolean.TRUE.equals(question.getStalenessDetected()),
        question.getReviewedAt());
  }

  private List<QueueItem> applyItemFilters(List<QueueItem> items, QueueFilter filter) {
    if (filter == null) {
      return items;
    }

    return items.stream()
        .filter(
            item ->
                filter.status() == null
                    || filter.status().isBlank()
                    || filter.status().equalsIgnoreCase(item.questionStatus())
                    || filter.status().equalsIgnoreCase(item.reviewStatus()))
        .filter(
            item ->
                filter.confidence() == null
                    || filter.confidence().isBlank()
                    || filter.confidence().equalsIgnoreCase(item.confidenceLevel()))
        .filter(item -> matchesSearch(item, filter.search()))
        .toList();
  }

  private boolean matchesSearch(QueueItem item, String search) {
    if (search == null || search.isBlank()) {
      return true;
    }
    String needle = search.toLowerCase(Locale.ROOT);
    return contains(item.questionText(), needle)
        || contains(item.questionNumber(), needle)
        || contains(item.questionnaireTitle(), needle)
        || contains(item.category(), needle)
        || contains(item.aiAnswer(), needle)
        || contains(item.finalAnswer(), needle);
  }

  private boolean contains(String haystack, String lowercaseNeedle) {
    return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
  }

  /**
   * Overdue first, then soonest due, then lowest confidence — the order an SME would work the queue
   * if they were triaging by hand.
   */
  private Comparator<QueueItem> queueOrdering() {
    return Comparator.comparing(QueueItem::overdue, Comparator.reverseOrder())
        .thenComparing(QueueItem::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(
            item -> item.confidenceScore() == null ? Integer.MAX_VALUE : item.confidenceScore())
        .thenComparing(QueueItem::questionNumber, Comparator.nullsLast(Comparator.naturalOrder()));
  }

  private QueueStats buildStats(UUID tenantId, List<QueueItem> items) {
    long pending =
        items.stream()
            .filter(i -> SMEAssignmentStatus.PENDING.name().equals(i.reviewStatus()))
            .count();
    long inProgress =
        items.stream()
            .filter(i -> SMEAssignmentStatus.IN_PROGRESS.name().equals(i.reviewStatus()))
            .count();
    long overdue = items.stream().filter(QueueItem::overdue).count();
    long highConfidence =
        items.stream().filter(i -> ConfidenceLevel.HIGH.name().equals(i.confidenceLevel())).count();

    // Approved/rejected counts are tenant-wide totals, not filtered — they are
    // progress indicators rather than properties of the current view.
    long approved =
        questionRepository
            .findByTenantIdAndStatusAndDeletedAtIsNull(
                tenantId, VantaQuestion.QuestionStatus.APPROVED)
            .size();
    long rejected =
        questionRepository
            .findByTenantIdAndStatusAndDeletedAtIsNull(
                tenantId, VantaQuestion.QuestionStatus.REJECTED)
            .size();

    return new QueueStats(
        items.size(),
        pending,
        inProgress,
        approved,
        rejected,
        overdue,
        highConfidence,
        assignmentRepository.countActiveSmes(tenantId));
  }

  /** Single item, used to broadcast a fresh row after an inline edit. */
  @Transactional(readOnly = true)
  public Optional<QueueItem> getItem(UUID questionId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      return Optional.empty();
    }

    return questionRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(questionId, tenantId)
        .map(
            question -> {
              SMEAssignment assignment =
                  assignmentRepository.findOpenAssignment(tenantId, questionId).orElse(null);
              Map<UUID, SMERoutingService.SMEProfile> profiles =
                  assignment != null
                      ? smeRoutingService.getProfilesByUserIds(Set.of(assignment.getSmeUserId()))
                      : Map.of();
              return toQueueItem(
                  question,
                  assignment,
                  profiles,
                  loadQuestionnaireTitles(List.of(question)),
                  loadCitations(List.of(question)));
            });
  }
}
