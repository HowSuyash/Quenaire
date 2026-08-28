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
package com.complyai.ai.vanta;

import com.complyai.ai.llm.LlmClient;
import com.complyai.ai.program.ProgramAwarenessService;
import com.complyai.domain.answer.Answer;
import com.complyai.domain.answer.AnswerRepository;
import com.complyai.domain.document.Document;
import com.complyai.domain.document.DocumentChunk;
import com.complyai.domain.questionnaire.vanta.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VantaReasoningEngine {

  private static final Logger log = LoggerFactory.getLogger(VantaReasoningEngine.class);

  /** How many live program facts to put in front of the model. */
  private static final int MAX_PROGRAM_FACTS = 5;

  private final AnswerLibraryService answerLibraryService;
  private final RetrievalService retrievalService;
  private final EmbeddingService embeddingService;
  private final ObjectMapper objectMapper;
  private final AnswerRepository answerRepository;
  private final ProgramAwarenessService programAwarenessService;
  private final LlmClient llmClient;

  public VantaReasoningEngine(
      AnswerLibraryService answerLibraryService,
      RetrievalService retrievalService,
      EmbeddingService embeddingService,
      ObjectMapper objectMapper,
      AnswerRepository answerRepository,
      ProgramAwarenessService programAwarenessService,
      LlmClient llmClient) {
    this.answerLibraryService = answerLibraryService;
    this.retrievalService = retrievalService;
    this.embeddingService = embeddingService;
    this.objectMapper = objectMapper;
    this.answerRepository = answerRepository;
    this.programAwarenessService = programAwarenessService;
    this.llmClient = llmClient;
  }

  public ReasoningResult reason(VantaQuestion question, List<Document> documents) {
    log.debug(
        "Reasoning for question: {} (type: {})",
        question.getQuestionNumber(),
        question.getQuestionType());

    // Tier 1: Check Answer Library for exact match
    Optional<AnswerLibraryEntry> libraryMatch =
        answerLibraryService.findExactMatch(question.getText(), question.getQuestionType());

    if (libraryMatch.isPresent()) {
      AnswerLibraryEntry entry = libraryMatch.get();
      answerLibraryService.incrementUsage(entry.getId());

      return ReasoningResult.builder()
          .answer(entry.getApprovedAnswer())
          .confidenceScore(100)
          .confidenceLevel(ConfidenceLevel.HIGH)
          .source(ReasoningSource.ANSWER_LIBRARY)
          .libraryEntryId(entry.getId())
          .citations(
              entry.getCitationIds().stream()
                  .map(this::getCitation)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList()))
          .explanation("Exact match found in Answer Library (previously approved)")
          .selectedOption(mapAnswerToOption(entry.getApprovedAnswer(), question.getOptions()))
          .build();
    }

    // Tier 1b: Semantic match in Answer Library
    List<AnswerLibraryEntry> semanticMatches =
        answerLibraryService.findSemanticMatches(question.getText(), question.getQuestionType(), 3);

    if (!semanticMatches.isEmpty()) {
      AnswerLibraryEntry bestMatch = semanticMatches.get(0);
      int similarityScore =
          calculateSimilarityScore(question.getText(), bestMatch.getQuestionText());

      if (similarityScore >= 90) {
        answerLibraryService.incrementUsage(bestMatch.getId());
        return ReasoningResult.builder()
            .answer(bestMatch.getApprovedAnswer())
            .confidenceScore(similarityScore)
            .confidenceLevel(ConfidenceLevel.fromScore(similarityScore))
            .source(ReasoningSource.ANSWER_LIBRARY_SEMANTIC)
            .libraryEntryId(bestMatch.getId())
            .citations(
                bestMatch.getCitationIds().stream()
                    .map(this::getCitation)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()))
            .explanation("High similarity match in Answer Library (" + similarityScore + "%)")
            .selectedOption(mapAnswerToOption(bestMatch.getApprovedAnswer(), question.getOptions()))
            .build();
      }
    }

    // Check for cross-questionnaire learning
    Optional<Answer> crossQuestionnaireAnswer = findCrossQuestionnaireAnswer(question);
    if (crossQuestionnaireAnswer.isPresent()) {
      Answer crossAnswer = crossQuestionnaireAnswer.get();
      return ReasoningResult.builder()
          .answer(crossAnswer.getContent())
          .confidenceScore(90)
          .confidenceLevel(ConfidenceLevel.HIGH)
          .source(ReasoningSource.CROSS_QUESTIONNAIRE)
          .libraryEntryId(crossAnswer.getId())
          .citations(
              crossAnswer.getCitationIds().stream()
                  .map(this::getCitation)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList()))
          .explanation(
              "Reused answer from similar question in another questionnaire (cross-questionnaire learning)")
          .selectedOption(mapAnswerToOption(crossAnswer.getContent(), question.getOptions()))
          .build();
    }

    // Tier 2: Document RAG with Chain-of-Thought
    RetrievalService.RetrievalResult retrievalResult =
        retrievalService.retrieve(question.getText(), documents, 5);

    if (retrievalResult.getChunks().isEmpty()) {
      return ReasoningResult.builder()
          .answer("Unable to find relevant information in knowledge base.")
          .confidenceScore(0)
          .confidenceLevel(ConfidenceLevel.LOW)
          .source(ReasoningSource.NO_MATCH)
          .explanation("No relevant documents found in knowledge base")
          .build();
    }

    // The completion is a labelled document: reasoning, answer, the model's own
    // confidence, anything it could not find. Only the answer section is the answer;
    // storing the whole thing put "THINKING:" and "CONFIDENCE: HIGH" into the PDF the
    // customer sends to their vendor.
    ChainOfThoughtResponse response =
        ChainOfThoughtResponse.parse(
            generateAnswerWithChainOfThought(question, retrievalResult.getChunks()));
    String generatedAnswer = response.answer();

    // Prefer the option the model named outright over guessing from prose.
    String selectedOption =
        response.selectedOption() != null
            ? matchOption(response.selectedOption(), question.getOptions())
            : mapAnswerToOption(generatedAnswer, question.getOptions());

    int confidenceScore = calculateConfidence(question, retrievalResult, response);
    ConfidenceLevel confidenceLevel = ConfidenceLevel.fromScore(confidenceScore);

    // Extract detailed citations with paragraph-level granularity
    List<Citation> citations = extractDetailedCitations(retrievalResult.getChunks());

    return ReasoningResult.builder()
        .answer(generatedAnswer)
        .confidenceScore(confidenceScore)
        .confidenceLevel(confidenceLevel)
        .source(ReasoningSource.DOCUMENT_RAG)
        .citations(citations)
        .explanation(buildExplanation(confidenceLevel, retrievalResult))
        .selectedOption(selectedOption)
        .retrievedChunks(retrievalResult.getChunks().size())
        .chainOfThought(response.thinking())
        .build();
  }

  /** Generate answer using Chain-of-Thought prompting for complex questions */
  private String generateAnswerWithChainOfThought(
      VantaQuestion question, List<DocumentChunk> chunks) {
    StringBuilder context = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      DocumentChunk chunk = chunks.get(i);
      context.append(
          String.format(
              "[Source %d: %s (Page %d, Section: %s)]\n%s\n\n",
              i + 1,
              chunk.getDocument().getTitle(),
              chunk.getPageNumber() != null ? chunk.getPageNumber() : 0,
              chunk.getSectionTitle() != null ? chunk.getSectionTitle() : "N/A",
              chunk.getContent()));
    }

    String prompt = buildChainOfThoughtPrompt(question, context.toString());
    return llmClient.complete(prompt);
  }

  /** Build Chain-of-Thought prompt for complex reasoning */
  private String buildChainOfThoughtPrompt(VantaQuestion question, String context) {
    StringBuilder prompt = new StringBuilder();

    prompt.append(
        "You are a senior security compliance expert answering a security questionnaire.\n\n");
    prompt.append("=== QUESTION ===\n");
    prompt.append("Question: ").append(question.getText()).append("\n");
    prompt.append("Type: ").append(question.getQuestionType().getDisplayName()).append("\n");

    if (question.getOptions() != null && !question.getOptions().isEmpty()) {
      prompt.append("Options: ").append(String.join(", ", question.getOptions())).append("\n");
      prompt.append("IMPORTANT: You MUST select exactly one of the provided options.\n");
    }

    if (question.getCategory() != null) {
      prompt.append("Category: ").append(question.getCategory()).append("\n");
    }
    if (question.getSection() != null) {
      prompt.append("Section: ").append(question.getSection()).append("\n");
    }

    appendProgramFacts(prompt, question);

    prompt.append("\n=== CONTEXT FROM SECURITY DOCUMENTS ===\n");
    prompt.append(context).append("\n");

    prompt.append("\n=== INSTRUCTIONS ===\n");
    prompt.append("1. Think step-by-step through the question using the provided context.\n");
    prompt.append(
        "2. Identify the specific security controls, policies, or procedures mentioned in the documents.\n");
    prompt.append("3. Extract exact quotes and citations from the source documents.\n");
    prompt.append("4. Synthesize a clear, accurate answer based ONLY on the provided context.\n");
    prompt.append("5. If the context doesn't contain enough information, state what's missing.\n");
    prompt.append("6. Where OBSERVED SECURITY PROGRAM STATE contradicts a document, trust the\n");
    prompt.append("   observed state - it comes from the live system - and say so explicitly.\n");
    prompt.append(
        "5. Provide confidence level: HIGH (explicit in docs), MEDIUM (inferred), LOW (speculative).\n\n");

    if (question.getQuestionType().hasOptions()) {
      prompt.append("IMPORTANT: End your response with 'SELECTED_OPTION: [exact option text]'.\n");
    }

    prompt.append("\n=== YOUR RESPONSE ===\n");
    prompt.append("THINKING:\n");
    prompt.append("[Think step by step here...]\n\n");
    prompt.append("ANSWER:\n");
    prompt.append("[Your answer here with citations like [Source 1], [Source 2]...]\n\n");
    if (question.getQuestionType().hasOptions()) {
      prompt.append("SELECTED_OPTION: [exact option text]\n");
    }
    prompt.append("CONFIDENCE: [HIGH/MEDIUM/LOW]\n");
    prompt.append("MISSING_INFO: [What information is missing, if any]\n");

    return prompt.toString();
  }

  /** Find answer from similar question in another questionnaire (cross-questionnaire learning) */
  private Optional<Answer> findCrossQuestionnaireAnswer(VantaQuestion question) {
    // In production, search across all questionnaires for similar questions
    // that have been approved with high confidence

    // For now, return empty
    return Optional.empty();
  }

  /** Extract detailed citations with paragraph-level granularity */
  private List<Citation> extractDetailedCitations(List<DocumentChunk> chunks) {
    return chunks.stream()
        .filter(c -> c.getRelevanceScore() > 0.5)
        .map(
            chunk -> {
              Citation citation = new Citation();
              citation.setDocumentId(chunk.getDocument().getId());
              citation.setDocumentTitle(chunk.getDocument().getTitle());
              citation.setChunkId(chunk.getId());
              citation.setPageNumber(chunk.getPageNumber());
              citation.setSectionTitle(chunk.getSectionTitle());
              citation.setParagraphIndex(chunk.getParagraphIndex());
              citation.setStartChar(chunk.getStartChar());
              citation.setEndChar(chunk.getEndChar());
              citation.setExactQuote(extractKeySentence(chunk.getContent()));
              citation.setRelevanceScore(chunk.getRelevanceScore());
              citation.setCitationType(Citation.CitationType.DOCUMENT_RAG);
              return citation;
            })
        .collect(Collectors.toList());
  }

  private String extractKeySentence(String content) {
    String[] sentences = content.split("[.!?]+");
    for (String s : sentences) {
      String trimmed = s.trim();
      if (trimmed.length() > 30 && trimmed.length() < 300) {
        return trimmed + ".";
      }
    }
    return content.substring(0, Math.min(300, content.length())) + "...";
  }

  private String buildExplanation(
      ConfidenceLevel level, RetrievalService.RetrievalResult retrieval) {
    return switch (level) {
      case HIGH ->
          "High confidence answer based on "
              + retrieval.getChunks().size()
              + " highly relevant document sections from certified sources (SOC 2, ISO 27001).";
      case MEDIUM ->
          "Medium confidence answer based on "
              + retrieval.getChunks().size()
              + " document sections. Some details may need verification.";
      case LOW ->
          "Low confidence - limited relevant documentation found. "
              + "Manual review by security team recommended.";
    };
  }

  /**
   * How much this answer can be trusted without a human reading it, from 0 to 100.
   *
   * <p>Three independent signals, because each is weak alone: how close the retrieved passages were
   * to the question, whether the model itself thought the context answered it, and whether the
   * answer visibly quotes its sources.
   *
   * <p>Answer length used to be worth up to 30 points, which paid the model to ramble and scored a
   * precise one-line answer below a vague paragraph. A further 5 points were awarded for the
   * response containing the literal strings "THINKING:" and "ANSWER:" -- scaffolding, present in
   * every response, and now stripped before this is called.
   */
  private int calculateConfidence(
      VantaQuestion question,
      RetrievalService.RetrievalResult retrieval,
      ChainOfThoughtResponse response) {
    String answer = response.answer();
    if (answer == null || answer.isBlank()) {
      return 0;
    }

    double score = 0;

    // How close the retrieved passages were to the question (0-45).
    if (!retrieval.getChunks().isEmpty()) {
      double avgRelevance =
          retrieval.getChunks().stream()
              .mapToDouble(DocumentChunk::getRelevanceScore)
              .average()
              .orElse(0.0);
      score += avgRelevance * 45;
    }

    // What the model made of its own context (0-30). It is the only signal that reflects
    // whether the passages actually answered the question rather than merely resembling it.
    score +=
        switch (response.statedConfidence()) {
          case HIGH -> 30;
          case MEDIUM -> 18;
          case LOW -> 5;
          case UNSTATED -> 12;
        };

    // Anything the model said it could not find is a reason for a human to look.
    if (response.missing().isPresent()) {
      score -= 15;
    }

    // Grounding: an answer that quotes or points at its sources (0-15).
    if (answer.contains("[Source") || answer.contains("\"")) {
      score += 15;
    }

    // A dropdown answer that maps onto an offered option (0-10).
    if (question.getQuestionType().hasOptions()
        && question.getOptions() != null
        && mapAnswerToOption(answer, question.getOptions()) != null) {
      score += 10;
    }

    // Audited sources carry more weight with the vendor reading the reply (0-10).
    if (retrieval.getChunks().stream().anyMatch(VantaReasoningEngine::isAuditedSource)) {
      score += 10;
    }

    return Math.min(100, Math.max(0, (int) Math.round(score)));
  }

  private static boolean isAuditedSource(DocumentChunk chunk) {
    String type = chunk.getDocument() == null ? null : chunk.getDocument().getSourceType();
    return type != null && (type.contains("SOC") || type.contains("ISO") || type.contains("CERT"));
  }

  /** Matches text the model named as its option against the options actually offered. */
  private String matchOption(String stated, List<String> options) {
    if (options == null || options.isEmpty()) {
      return null;
    }
    for (String option : options) {
      if (option.equalsIgnoreCase(stated)) {
        return option;
      }
    }
    return mapAnswerToOption(stated, options);
  }

  private String mapAnswerToOption(String answer, List<String> options) {
    if (options == null || options.isEmpty() || answer == null) return null;

    String lowerAnswer = answer.toLowerCase();

    // Exact match
    for (String opt : options) {
      if (opt.equalsIgnoreCase(answer.trim())) return opt;
    }

    // Partial match
    for (String opt : options) {
      if (lowerAnswer.contains(opt.toLowerCase()) || opt.toLowerCase().contains(lowerAnswer)) {
        return opt;
      }
    }

    // Keyword matching for Yes/No
    if (options.size() == 2) {
      String opt1 = options.get(0).toLowerCase();
      String opt2 = options.get(1).toLowerCase();

      if ((opt1.contains("yes") || opt1.contains("true"))
          && (opt2.contains("no") || opt2.contains("false"))) {
        if (lowerAnswer.contains("yes")
            || lowerAnswer.contains("true")
            || lowerAnswer.contains("we do")
            || lowerAnswer.contains("implemented")) {
          return options.get(0);
        }
        if (lowerAnswer.contains("no")
            || lowerAnswer.contains("false")
            || lowerAnswer.contains("not")
            || lowerAnswer.contains("don't")) {
          return options.get(1);
        }
      }
    }

    return null;
  }

  private int calculateSimilarityScore(String q1, String q2) {
    Set<String> words1 = new HashSet<>(Arrays.asList(q1.toLowerCase().split("\\s+")));
    Set<String> words2 = new HashSet<>(Arrays.asList(q2.toLowerCase().split("\\s+")));

    Set<String> intersection = new HashSet<>(words1);
    intersection.retainAll(words2);
    Set<String> union = new HashSet<>(words1);
    union.addAll(words2);

    return union.isEmpty()
        ? 0
        : (int) Math.round((double) intersection.size() / union.size() * 100);
  }

  private Citation getCitation(UUID citationId) {
    // Would fetch from repository
    return null;
  }

  // Builder pattern for result
  public static class ReasoningResult {
    private String answer;
    private int confidenceScore;
    private ConfidenceLevel confidenceLevel;
    private ReasoningSource source;
    private UUID libraryEntryId;
    private List<Citation> citations = new ArrayList<>();
    private String explanation;
    private String selectedOption;
    private int retrievedChunks;
    private String chainOfThought;

    public static ReasoningResultBuilder builder() {
      return new ReasoningResultBuilder();
    }

    // Getters
    public String getAnswer() {
      return answer;
    }

    public int getConfidenceScore() {
      return confidenceScore;
    }

    public ConfidenceLevel getConfidenceLevel() {
      return confidenceLevel;
    }

    public ReasoningSource getSource() {
      return source;
    }

    public UUID getLibraryEntryId() {
      return libraryEntryId;
    }

    public List<Citation> getCitations() {
      return citations;
    }

    public String getExplanation() {
      return explanation;
    }

    public String getSelectedOption() {
      return selectedOption;
    }

    public int getRetrievedChunks() {
      return retrievedChunks;
    }

    public String getChainOfThought() {
      return chainOfThought;
    }

    public static class ReasoningResultBuilder {
      private ReasoningResult result = new ReasoningResult();

      public ReasoningResultBuilder answer(String answer) {
        result.answer = answer;
        return this;
      }

      public ReasoningResultBuilder confidenceScore(int score) {
        result.confidenceScore = score;
        return this;
      }

      public ReasoningResultBuilder confidenceLevel(ConfidenceLevel level) {
        result.confidenceLevel = level;
        return this;
      }

      public ReasoningResultBuilder source(ReasoningSource source) {
        result.source = source;
        return this;
      }

      public ReasoningResultBuilder libraryEntryId(UUID id) {
        result.libraryEntryId = id;
        return this;
      }

      public ReasoningResultBuilder citations(List<Citation> citations) {
        result.citations = citations;
        return this;
      }

      public ReasoningResultBuilder explanation(String explanation) {
        result.explanation = explanation;
        return this;
      }

      public ReasoningResultBuilder selectedOption(String option) {
        result.selectedOption = option;
        return this;
      }

      public ReasoningResultBuilder retrievedChunks(int count) {
        result.retrievedChunks = count;
        return this;
      }

      public ReasoningResultBuilder chainOfThought(String chainOfThought) {
        result.chainOfThought = chainOfThought;
        return this;
      }

      public ReasoningResult build() {
        return result;
      }
    }
  }

  public enum ReasoningSource {
    ANSWER_LIBRARY,
    ANSWER_LIBRARY_SEMANTIC,
    DOCUMENT_RAG,
    CROSS_QUESTIONNAIRE,
    NO_MATCH
  }

  /**
   * Add live posture from connected systems (AWS Config, GitHub, Jira, Confluence) to the prompt.
   *
   * <p>Facts carry their source and observation date, and stale ones are flagged, so the model
   * qualifies rather than overstates. Grounding is best-effort: a missing connector just means
   * fewer facts.
   */
  private void appendProgramFacts(StringBuilder prompt, VantaQuestion question) {
    List<ProgramAwarenessService.ProgramFact> facts;
    try {
      facts = programAwarenessService.findRelevantFacts(question.getText(), MAX_PROGRAM_FACTS);
    } catch (Exception e) {
      log.debug("Could not load program facts for question {}", question.getId(), e);
      return;
    }

    if (facts.isEmpty()) {
      return;
    }

    prompt.append("\n=== OBSERVED SECURITY PROGRAM STATE (live systems) ===\n");
    for (ProgramAwarenessService.ProgramFact fact : facts) {
      prompt
          .append("- [")
          .append(fact.source())
          .append("] ")
          .append(fact.title())
          .append(" - status ")
          .append(fact.status());
      if (fact.detail() != null && !fact.detail().isBlank()) {
        prompt.append(": ").append(fact.detail());
      }
      prompt.append(" (observed ").append(fact.observedAt());
      if (fact.stale()) {
        prompt.append(", STALE - do not present as current without a caveat");
      }
      prompt.append(")");
      if (!fact.controls().isEmpty()) {
        prompt.append(" [maps to ").append(String.join(", ", fact.controls())).append("]");
      }
      prompt.append("\n");
    }
    prompt.append("\n");
  }
}
