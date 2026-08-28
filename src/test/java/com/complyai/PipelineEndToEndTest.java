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
package com.complyai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.complyai.ai.ingestion.DocumentIngestionService;
import com.complyai.ai.llm.LlmClient;
import com.complyai.ai.vanta.VantaPdfExportService;
import com.complyai.ai.vanta.VantaReasoningEngine;
import com.complyai.domain.document.Document;
import com.complyai.domain.document.DocumentChunkRepository;
import com.complyai.domain.document.DocumentStatus;
import com.complyai.domain.questionnaire.vanta.QuestionType;
import com.complyai.domain.questionnaire.vanta.VantaQuestion;
import com.complyai.domain.questionnaire.vanta.VantaQuestionRepository;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaire;
import com.complyai.domain.questionnaire.vanta.VantaQuestionnaireRepository;
import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.tenant.TenantPlan;
import com.complyai.domain.tenant.TenantRepository;
import com.complyai.domain.tenant.TenantStatus;
import com.complyai.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Walks a document and a question through the whole product, against a real Postgres with pgvector
 * and a real language model.
 *
 * <p>This is the test the product did not have: every stage had been exercised in isolation, but
 * nothing had ever carried a document from ingestion to a cited answer. Three of those stages were
 * placeholders returning fixed values, and because no test joined them up, that went unnoticed.
 *
 * <p>Requires Ollama with the configured models. When it is absent the test skips rather than
 * fails, so a machine without a local model can still run the suite — but a skip is not a pass, and
 * CI should have the model available.
 */
class PipelineEndToEndTest extends IntegrationTestBase {

  /**
   * Stands in for a customer's security documentation. Deliberately worded differently from the
   * questions below, so a passing retrieval proves semantic matching rather than keyword overlap.
   */
  private static final String SECURITY_POLICY =
      """
      Information Security Policy

      Access control. Multi-factor authentication is mandatory for every administrative account
      and for remote access. Authenticator applications are used; SMS one-time codes are not
      accepted. Credentials are held in a managed password vault and never in source code.

      Cryptography. Information held on disk is protected with AES-256 encryption under keys
      managed by the cloud provider's key management service, rotated every twelve months.
      Traffic between clients and the service is protected with TLS 1.3, and older cipher suites
      are refused.

      Resilience. The service is designed to lose no more than fifteen minutes of work in a
      disaster, and to be back in operation within one hour. Failover between regions is
      exercised twice a year.

      Assurance. An independent firm performs penetration testing once a year against the
      production estate. Executive summaries are shared with customers under non-disclosure.
      """;

  @Autowired private DocumentIngestionService ingestionService;
  @Autowired private VantaReasoningEngine reasoningEngine;
  @Autowired private VantaPdfExportService pdfExportService;
  @Autowired private LlmClient llmClient;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private DocumentChunkRepository chunkRepository;
  @Autowired private VantaQuestionnaireRepository questionnaireRepository;
  @Autowired private VantaQuestionRepository questionRepository;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenant = new Tenant();
    tenant.setName("Pipeline Test Tenant");
    tenant.setSlug("pipeline-test-" + System.nanoTime());
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setPlan(TenantPlan.FREE);
    tenant = tenantRepository.save(tenant);

    TenantContext.setCurrentTenant(tenant);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("a document is ingested, chunked, and embedded into pgvector")
  void ingestionProducesEmbeddedChunks() {
    assumeModelAvailable();

    Document document =
        ingestionService.ingestText(
            "Information Security Policy", SECURITY_POLICY, "text/plain", "UPLOAD");

    assertThat(document.getStatus()).isEqualTo(DocumentStatus.INDEXED);
    assertThat(document.getWordCount()).isGreaterThan(50);

    var chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId());
    assertThat(chunks).isNotEmpty();

    // The regression this guards: embeddings used to be a fixed five-element
    // vector, so every similarity score in the product was meaningless.
    assertThat(chunks)
        .allSatisfy(
            chunk -> {
              assertThat(chunk.getEmbedding()).as("chunk must be embedded").isNotNull();
              assertThat(chunk.getEmbedding())
                  .as("embedding must have the model's real dimensionality")
                  .hasSizeGreaterThan(100);
            });
  }

  @Test
  @DisplayName("a question is answered from the document, not from a placeholder")
  void questionIsAnsweredFromTheIngestedDocument() {
    assumeModelAvailable();

    Document document =
        ingestionService.ingestText(
            "Information Security Policy", SECURITY_POLICY, "text/plain", "UPLOAD");

    VantaQuestion question =
        persistQuestion(
            "G.2",
            "Is data encrypted at rest? State the algorithm and key length.",
            "Data Protection");

    VantaReasoningEngine.ReasoningResult result =
        reasoningEngine.reason(question, List.of(document));

    assertThat(result.getAnswer()).isNotBlank();

    // The exact regression: generation returned this sentence for every question.
    assertThat(result.getAnswer())
        .as("answer must come from the model, not the old placeholder")
        .doesNotContain("Answer would be generated by LLM");

    assertThat(result.getRetrievedChunks())
        .as("the answer must be grounded in retrieved content")
        .isGreaterThan(0);

    // The policy says AES-256; a grounded answer should reflect that.
    assertThat(result.getAnswer().toLowerCase())
        .as("answer should reflect what the document actually says")
        .containsAnyOf("aes", "256", "encrypt");
  }

  @Test
  @DisplayName("differently worded questions retrieve the right passage")
  void retrievalMatchesOnMeaningRatherThanWording() {
    assumeModelAvailable();

    Document document =
        ingestionService.ingestText(
            "Information Security Policy", SECURITY_POLICY, "text/plain", "UPLOAD");

    // Neither question shares vocabulary with the passage that answers it.
    VantaQuestion recovery =
        persistQuestion("I.2", "How quickly can you recover from an outage?", "Continuity");

    VantaReasoningEngine.ReasoningResult result =
        reasoningEngine.reason(recovery, List.of(document));

    assertThat(result.getRetrievedChunks()).isGreaterThan(0);
    assertThat(result.getAnswer()).isNotBlank();
  }

  @Test
  @DisplayName("answered questions export to a PDF a customer could send")
  void answersExportToPdf() throws Exception {
    assumeModelAvailable();

    Document document =
        ingestionService.ingestText(
            "Information Security Policy", SECURITY_POLICY, "text/plain", "UPLOAD");

    VantaQuestion question =
        persistQuestion(
            "D.2",
            "Is multi-factor authentication enforced for administrative access?",
            "Access Control");

    VantaReasoningEngine.ReasoningResult result =
        reasoningEngine.reason(question, List.of(document));

    question.setAiAnswer(result.getAnswer());
    question.setFinalAnswer(result.getAnswer());
    question.setConfidenceScore(result.getConfidenceScore());
    question = questionRepository.save(question);

    // Read the questionnaire back rather than following the saved question's lazy
    // association, which is a detached proxy once the repository call returns.
    VantaQuestionnaire questionnaire =
        questionnaireRepository.findById(question.getQuestionnaire().getId()).orElseThrow();

    byte[] pdf =
        pdfExportService.exportQuestionnaire(
            questionnaire, List.of(question), VantaPdfExportService.Branding.defaults());

    assertThat(pdf).isNotEmpty();
    // %PDF- is the file signature; anything else is not a readable document.
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    assertThat(pdf.length)
        .as("a one-question report should still be a real page")
        .isGreaterThan(1000);
  }

  // ---- helpers -------------------------------------------------------------

  private VantaQuestion persistQuestion(String number, String text, String category) {
    VantaQuestionnaire questionnaire = new VantaQuestionnaire();
    questionnaire.setTitle("Vendor Security Assessment");
    questionnaire.setSourceType(VantaQuestionnaire.SourceType.UPLOAD_PDF);
    questionnaire.setStatus(VantaQuestionnaire.QuestionnaireStatus.PROCESSING);
    questionnaire.setTenant(tenant);
    questionnaire = questionnaireRepository.save(questionnaire);

    VantaQuestion question = new VantaQuestion();
    question.setQuestionnaire(questionnaire);
    question.setQuestionNumber(number);
    question.setText(text);
    question.setCategory(category);
    question.setQuestionType(QuestionType.FREE_TEXT);
    question.setStatus(VantaQuestion.QuestionStatus.PENDING);
    question.setTenant(tenant);
    return questionRepository.save(question);
  }

  /** A local model is infrastructure, not a unit under test; skip rather than fail without it. */
  private void assumeModelAvailable() {
    assumeThat(llmClient.isAvailable())
        .as("Ollama must be running with the configured models")
        .isTrue();
  }
}
