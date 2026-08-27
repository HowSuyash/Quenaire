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
package com.complyai.domain.questionnaire.vanta;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vanta_citations")
public class Citation {

  @Id
  @GeneratedValue
  @Column(columnDefinition = "uuid", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "document_title", nullable = false, length = 500)
  private String documentTitle;

  @Column(name = "document_version")
  private String documentVersion;

  @Column(name = "chunk_id", nullable = false)
  private UUID chunkId;

  @Column(name = "page_number")
  private Integer pageNumber;

  @Column(name = "section_title", length = 255)
  private String sectionTitle;

  @Column(name = "paragraph_index")
  private Integer paragraphIndex;

  @Column(name = "start_char")
  private Integer startChar;

  @Column(name = "end_char")
  private Integer endChar;

  @Column(name = "exact_quote", columnDefinition = "TEXT", nullable = false)
  private String exactQuote;

  @Column(name = "relevance_score", nullable = false)
  private Double relevanceScore;

  @Column(name = "citation_type")
  @Enumerated(EnumType.STRING)
  private CitationType citationType;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  // vanta_citations already has a deleted_at column and CitationRepository
  // filters on it; the entity simply never mapped it, which broke every
  // derived query naming DeletedAtIsNull.
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  public enum CitationType {
    DIRECT_MATCH("Direct match from Answer Library"),
    DOCUMENT_RAG("Retrieved from Document RAG"),
    INFERRED("Inferred from context"),
    HUMAN_VERIFIED("Human verified");

    private final String description;

    CitationType(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public void setDocumentId(UUID documentId) {
    this.documentId = documentId;
  }

  public String getDocumentTitle() {
    return documentTitle;
  }

  public void setDocumentTitle(String documentTitle) {
    this.documentTitle = documentTitle;
  }

  public String getDocumentVersion() {
    return documentVersion;
  }

  public void setDocumentVersion(String documentVersion) {
    this.documentVersion = documentVersion;
  }

  public UUID getChunkId() {
    return chunkId;
  }

  public void setChunkId(UUID chunkId) {
    this.chunkId = chunkId;
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

  public String getExactQuote() {
    return exactQuote;
  }

  public void setExactQuote(String exactQuote) {
    this.exactQuote = exactQuote;
  }

  public Double getRelevanceScore() {
    return relevanceScore;
  }

  public void setRelevanceScore(Double relevanceScore) {
    this.relevanceScore = relevanceScore;
  }

  public CitationType getCitationType() {
    return citationType;
  }

  public void setCitationType(CitationType citationType) {
    this.citationType = citationType;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(OffsetDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
