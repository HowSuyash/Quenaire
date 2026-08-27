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
package com.complyai.ai.generation;

import com.complyai.domain.answer.Answer;
import com.complyai.domain.answer.AnswerRepository;
import com.complyai.domain.answer.AnswerStatus;
import com.complyai.domain.questionnaire.Question;
import com.complyai.domain.questionnaire.QuestionRepository;
import com.complyai.domain.questionnaire.QuestionStatus;
import com.complyai.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerService {

  private final QuestionRepository questionRepository;
  private final AnswerRepository answerRepository;
  private final AnswerGenerationService generationService;

  public AnswerService(
      QuestionRepository questionRepository,
      AnswerRepository answerRepository,
      AnswerGenerationService generationService) {
    this.questionRepository = questionRepository;
    this.answerRepository = answerRepository;
    this.generationService = generationService;
  }

  @Transactional
  public Answer generateAnswer(UUID questionId) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    Question question =
        questionRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(questionId, tenantId)
            .orElseThrow(() -> new NotFoundException("Question not found"));

    var generated = generationService.generateAnswer(question);

    Answer answer = new Answer();
    answer.setQuestionId(questionId);
    answer.setContent(generated.content());
    answer.setAiGeneratedContent(generated.content());
    answer.setCitationsJson(toCitationsJson(generated.citations()));
    answer.setConfidenceScore(generated.confidenceScore());
    answer.setStatus(AnswerStatus.AI_GENERATED);
    answer.setTenantId(tenantId);

    answer = answerRepository.save(answer);

    question.setStatus(QuestionStatus.AI_DRAFTED);
    questionRepository.save(question);

    return answer;
  }

  @Transactional
  public Answer generateAnswersForQuestionnaire(UUID questionnaireId) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    List<Question> questions =
        questionRepository.findByQuestionnaireIdAndDeletedAtIsNull(questionnaireId);

    for (Question question : questions) {
      if (question.getStatus() == QuestionStatus.PENDING
          || question.getStatus() == QuestionStatus.NEEDS_REVIEW) {
        try {
          generateAnswer(question.getId());
        } catch (Exception e) {
          // Log error but continue with other questions
        }
      }
    }

    return null; // Could return summary
  }

  @Transactional
  public Answer updateAnswer(
      UUID answerId, String content, AnswerStatus status, String reviewNotes) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    Answer answer =
        answerRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(answerId, tenantId)
            .orElseThrow(() -> new NotFoundException("Answer not found"));

    if (content != null) {
      answer.setContent(content);
    }
    if (status != null) {
      answer.setStatus(status);
      if (status == AnswerStatus.APPROVED || status == AnswerStatus.REJECTED) {
        answer.setReviewedAt(Instant.now());
        answer.setReviewedById(getCurrentUserId());
      }
    }
    if (reviewNotes != null) {
      answer.setReviewNotes(reviewNotes);
    }

    return answerRepository.save(answer);
  }

  @Transactional
  public Answer approveAnswer(UUID answerId) {
    return updateAnswer(answerId, null, AnswerStatus.APPROVED, null);
  }

  @Transactional
  public Answer rejectAnswer(UUID answerId, String reviewNotes) {
    return updateAnswer(answerId, null, AnswerStatus.REJECTED, reviewNotes);
  }

  private String toCitationsJson(List<AnswerGenerationService.Citation> citations) {
    if (citations == null || citations.isEmpty()) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < citations.size(); i++) {
      var c = citations.get(i);
      if (i > 0) sb.append(",");
      sb.append(
          String.format(
              "{\"chunkId\":\"%s\",\"documentId\":\"%s\",\"chunkIndex\":%d,\"startChar\":%d,\"endChar\":%d,\"similarityScore\":%.4f,\"snippet\":\"%s\"}",
              c.chunkId(),
              c.documentId(),
              c.chunkIndex(),
              c.startChar(),
              c.endChar(),
              c.similarityScore(),
              escapeJson(c.snippet())));
    }
    sb.append("]");
    return sb.toString();
  }

  private String escapeJson(String s) {
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private UUID getCurrentUserId() {
    return null;
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }
}
