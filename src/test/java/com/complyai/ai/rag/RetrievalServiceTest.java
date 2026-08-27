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
package com.complyai.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.complyai.IntegrationTestBase;
import com.complyai.ai.ingestion.EmbeddingService;
import com.complyai.domain.document.Document;
import com.complyai.domain.document.DocumentChunk;
import com.complyai.domain.document.DocumentChunkRepository;
import com.complyai.domain.document.DocumentRepository;
import com.complyai.domain.document.DocumentStatus;
import com.complyai.domain.questionnaire.Question;
import com.complyai.domain.questionnaire.QuestionRepository;
import com.complyai.domain.questionnaire.QuestionStatus;
import com.complyai.domain.questionnaire.QuestionType;
import com.complyai.domain.questionnaire.Questionnaire;
import com.complyai.domain.questionnaire.QuestionnaireRepository;
import com.complyai.domain.questionnaire.QuestionnaireSourceType;
import com.complyai.domain.questionnaire.QuestionnaireStatus;
import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.tenant.TenantPlan;
import com.complyai.domain.tenant.TenantRepository;
import com.complyai.domain.tenant.TenantStatus;
import com.complyai.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class RetrievalServiceTest extends IntegrationTestBase {

  @Autowired RetrievalService retrievalService;

  @Autowired TenantRepository tenantRepository;

  @Autowired DocumentRepository documentRepository;

  @Autowired DocumentChunkRepository chunkRepository;

  @Autowired QuestionnaireRepository questionnaireRepository;

  @Autowired QuestionRepository questionRepository;

  @MockBean EmbeddingService embeddingService;

  @Test
  void retrieveWithNoDocuments() {
    Tenant tenant = createTenant();
    TenantContext.setCurrentTenant(tenant);

    when(embeddingService.embed(anyString())).thenReturn(new float[1536]);

    Question question = createQuestion(tenant);

    List<RetrievalService.RetrievalResult> results = retrievalService.retrieve(question);

    assertThat(results).isEmpty();
  }

  @Test
  void retrieveWithMatchingDocuments() {
    Tenant tenant = createTenant();
    TenantContext.setCurrentTenant(tenant);

    // Create document with chunks
    Document document = createDocument(tenant);
    DocumentChunk chunk =
        createChunk(document, tenant, "We use AES-256 encryption for all data at rest.");
    chunkRepository.save(chunk);

    // Mock embedding to return similar vector
    float[] queryEmbedding = new float[1536];
    queryEmbedding[0] = 1.0f;
    when(embeddingService.embed(anyString())).thenReturn(queryEmbedding);

    // Mock chunk embedding to be similar
    float[] chunkEmbedding = new float[1536];
    chunkEmbedding[0] = 0.9f;
    chunk.setEmbedding(new com.pgvector.PGvector(chunkEmbedding));
    chunkRepository.save(chunk);

    Question question = createQuestion(tenant);

    List<RetrievalService.RetrievalResult> results = retrievalService.retrieve(question);

    assertThat(results).isNotEmpty();
    assertThat(results.get(0).content()).contains("AES-256");
  }

  private Tenant createTenant() {
    Tenant tenant = new Tenant();
    tenant.setName("Test Tenant");
    tenant.setSlug("test-tenant");
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setPlan(TenantPlan.FREE);
    tenant.setSettingsJson("{}");
    tenant.setBrandingJson("{}");
    return tenantRepository.save(tenant);
  }

  private Document createDocument(Tenant tenant) {
    Document document = new Document();
    document.setTitle("Security Policy");
    document.setSourceType("UPLOAD");
    document.setMimeType("text/plain");
    document.setStatus(DocumentStatus.INDEXED);
    document.setTenant(tenant);
    document.setContentHash("hash123");
    return documentRepository.save(document);
  }

  private DocumentChunk createChunk(Document document, Tenant tenant, String content) {
    DocumentChunk chunk = new DocumentChunk();
    chunk.setDocument(document);
    chunk.setContent(content);
    chunk.setChunkIndex(0);
    chunk.setTokenCount(content.split("\\s+").length);
    chunk.setStartChar(0);
    chunk.setEndChar(content.length());
    chunk.setTenant(tenant);
    return chunk;
  }

  private Question createQuestion(Tenant tenant) {
    Questionnaire questionnaire = new Questionnaire();
    questionnaire.setTitle("Test Questionnaire");
    questionnaire.setSourceType(QuestionnaireSourceType.UPLOAD_EXCEL);
    questionnaire.setStatus(QuestionnaireStatus.READY_FOR_REVIEW);
    questionnaire.setTotalQuestions(1);
    questionnaire.setTenant(tenant);
    questionnaire = questionnaireRepository.save(questionnaire);

    Question question = new Question();
    question.setQuestionnaire(questionnaire);
    question.setText("What encryption standard do you use?");
    question.setQuestionType(QuestionType.FREE_TEXT);
    question.setStatus(QuestionStatus.PENDING);
    question.setTenant(tenant);
    return questionRepository.save(question);
  }
}
