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

import com.complyai.ai.llm.LlmClient;
import com.complyai.ai.rag.RetrievalService;
import com.complyai.ai.rag.RetrievalService.RetrievalResult;
import com.complyai.domain.questionnaire.Question;
import com.complyai.domain.questionnaire.QuestionType;
import com.complyai.security.TenantContext;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnswerGenerationService {

  private final RetrievalService retrievalService;
  private final LlmClient llmClient;
  private final int maxTokens;

  public AnswerGenerationService(
      RetrievalService retrievalService,
      LlmClient llmClient,
      @Value("${app.ollama.max-tokens:2048}") int maxTokens) {
    this.retrievalService = retrievalService;
    this.llmClient = llmClient;
    this.maxTokens = maxTokens;
  }

  public GeneratedAnswer generateAnswer(Question question) {
    TenantContext.requireTenant();

    List<RetrievalResult> sources = retrievalService.retrieve(question);
    String context = buildContext(sources);
    String prompt = buildPrompt(question, context);

    String generatedText = callOllama(prompt);

    List<Citation> citations = buildCitations(sources, generatedText);
    double confidence = calculateConfidence(sources, question.getQuestionType());

    return new GeneratedAnswer(generatedText, citations, confidence);
  }

  private String buildContext(List<RetrievalResult> sources) {
    if (sources.isEmpty()) {
      return "No relevant documents found.";
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < sources.size(); i++) {
      RetrievalResult source = sources.get(i);
      sb.append(
          String.format(
              "[Source %d] (Document: %s, Chunk: %d, Similarity: %.2f)\n%s\n\n",
              i + 1,
              source.documentId(),
              source.chunkIndex(),
              source.similarityScore(),
              source.content()));
    }
    return sb.toString();
  }

  private String buildPrompt(Question question, String context) {
    String typeInstruction = getTypeInstruction(question.getQuestionType());

    return String.format(
        """
                You are an expert security compliance analyst. Answer the following question based ONLY on the provided context.

                Context:
                %s

                Question: %s
                Question Type: %s
                %s

                Instructions:
                1. Answer based ONLY on the provided context
                2. If the context doesn't contain enough information, state that clearly
                3. Cite sources using [Source N] format
                4. %s
                5. Be concise and professional

                Answer:
                """,
        context,
        question.getText(),
        question.getQuestionType(),
        question.getOptionsJson() != null ? "Options: " + question.getOptionsJson() : "",
        typeInstruction);
  }

  private String getTypeInstruction(QuestionType type) {
    return switch (type) {
      case FREE_TEXT -> "Provide a comprehensive text answer with citations";
      case SINGLE_CHOICE ->
          "Select ONE option from the provided choices and explain your reasoning";
      case MULTIPLE_CHOICE ->
          "Select ALL applicable options from the provided choices and explain your reasoning";
      case YES_NO -> "Answer Yes or No with explanation and citations";
      case NUMERIC -> "Provide a numeric answer with units if applicable";
      case DATE -> "Provide a date in ISO format (YYYY-MM-DD)";
      case FILE_UPLOAD -> "Describe what file/content would be needed";
      case TABLE -> "Provide a structured table response in Markdown format";
    };
  }

  /** Generation goes through the shared client so the model is chosen in one place. */
  private String callOllama(String prompt) {
    return llmClient.complete(prompt, maxTokens);
  }

  private List<Citation> buildCitations(List<RetrievalResult> sources, String generatedText) {
    return sources.stream()
        .filter(s -> generatedText.contains("[Source " + (sources.indexOf(s) + 1) + "]"))
        .map(
            s ->
                new Citation(
                    s.chunkId(),
                    s.documentId(),
                    s.chunkIndex(),
                    s.startChar(),
                    s.endChar(),
                    s.similarityScore(),
                    s.content().substring(0, Math.min(200, s.content().length())) + "..."))
        .collect(Collectors.toList());
  }

  private double calculateConfidence(List<RetrievalResult> sources, QuestionType questionType) {
    if (sources.isEmpty()) {
      return 0.1;
    }

    double avgSimilarity =
        sources.stream().mapToDouble(RetrievalResult::similarityScore).average().orElse(0.0);

    double typeConfidence =
        switch (questionType) {
          case FREE_TEXT -> 0.8;
          case SINGLE_CHOICE, MULTIPLE_CHOICE -> 0.85;
          case YES_NO -> 0.9;
          case NUMERIC, DATE -> 0.75;
          case FILE_UPLOAD, TABLE -> 0.7;
        };

    return Math.min(0.95, avgSimilarity * typeConfidence);
  }

  public record GeneratedAnswer(String content, List<Citation> citations, double confidenceScore) {}

  public record Citation(
      UUID chunkId,
      UUID documentId,
      int chunkIndex,
      int startChar,
      int endChar,
      double similarityScore,
      String snippet) {}

  public static class GenerationException extends RuntimeException {
    public GenerationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
