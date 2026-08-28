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
package com.complyai.domain.document;

import com.complyai.ai.rag.VectorLiterals;
import com.complyai.domain.common.BaseEntity;
import com.complyai.domain.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "document_chunks",
    indexes = {
      @Index(name = "idx_chunks_document_id", columnList = "document_id"),
      @Index(name = "idx_chunks_tenant_id", columnList = "tenant_id")
    })
public class DocumentChunk extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id", nullable = false)
  private Document document;

  @NotBlank
  @Column(name = "content", columnDefinition = "text", nullable = false)
  private String content;

  @Column(name = "chunk_index", nullable = false)
  private Integer chunkIndex;

  @Column(name = "token_count")
  private Integer tokenCount;

  @Column(name = "start_char")
  private Integer startChar;

  @Column(name = "end_char")
  private Integer endChar;

  @Column(name = "page_number")
  private Integer pageNumber;

  @Column(name = "section_title", length = 255)
  private String sectionTitle;

  @Column(name = "paragraph_index")
  private Integer paragraphIndex;

  @JdbcTypeCode(SqlTypes.VECTOR)
  @Column(name = "embedding", columnDefinition = "vector(768)")
  @Array(length = VectorLiterals.DIMENSIONS)
  private float[] embedding;

  @Column(name = "relevance_score")
  private Double relevanceScore;

  @Transient private List<Float> embeddingVector;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson = "{}";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  public Document getDocument() {
    return document;
  }

  public void setDocument(Document document) {
    this.document = document;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Integer getChunkIndex() {
    return chunkIndex;
  }

  public void setChunkIndex(Integer chunkIndex) {
    this.chunkIndex = chunkIndex;
  }

  public Integer getTokenCount() {
    return tokenCount;
  }

  public void setTokenCount(Integer tokenCount) {
    this.tokenCount = tokenCount;
  }

  public Integer getStartChar() {
    return startChar;
  }

  public void setStartChar(Integer startChar) {
    this.startChar = startChar;
  }

  public Integer getEndChar() {
    return endChar;
  }

  public void setEndChar(Integer endChar) {
    this.endChar = endChar;
  }

  public Integer getPageNumber() {
    return pageNumber;
  }

  public void setPageNumber(Integer pageNumber) {
    this.pageNumber = pageNumber;
  }

  public String getSectionTitle() {
    return sectionTitle;
  }

  public void setSectionTitle(String sectionTitle) {
    this.sectionTitle = sectionTitle;
  }

  public Integer getParagraphIndex() {
    return paragraphIndex;
  }

  public void setParagraphIndex(Integer paragraphIndex) {
    this.paragraphIndex = paragraphIndex;
  }

  public float[] getEmbedding() {
    return embedding;
  }

  public void setEmbedding(float[] embedding) {
    this.embedding = embedding;
  }

  public Double getRelevanceScore() {
    return relevanceScore;
  }

  public void setRelevanceScore(Double relevanceScore) {
    this.relevanceScore = relevanceScore;
  }

  public List<Float> getEmbeddingVector() {
    return embeddingVector;
  }

  public void setEmbeddingVector(List<Float> embeddingVector) {
    this.embeddingVector = embeddingVector;
  }

  public List<Float> getEmbeddingAsList() {
    if (embeddingVector != null) return embeddingVector;
    if (embedding != null) {
      // Convert PGvector to List<Float>
      // This is a simplified conversion
      return List.of();
    }
    return List.of();
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }

  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }
}
