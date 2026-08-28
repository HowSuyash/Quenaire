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

import com.complyai.domain.common.BaseEntity;
import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "documents",
    indexes = {
      @Index(name = "idx_documents_tenant_id", columnList = "tenant_id"),
      @Index(name = "idx_documents_status", columnList = "status"),
      @Index(name = "idx_documents_created_by", columnList = "created_by_id")
    })
public class Document extends BaseEntity {

  @NotBlank
  @Size(max = 500)
  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @NotBlank
  @Size(max = 100)
  @Column(name = "source_type", nullable = false)
  private String sourceType; // UPLOAD, CONFLUENCE, GITHUB, NOTION, URL

  @Size(max = 500)
  @Column(name = "source_url")
  private String sourceUrl;

  @Size(max = 100)
  @Column(name = "source_id")
  private String sourceId;

  @Size(max = 50)
  @Column(name = "file_name")
  private String fileName;

  @Size(max = 100)
  @Column(name = "mime_type")
  private String mimeType;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "storage_path")
  private String storagePath;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private DocumentStatus status = DocumentStatus.UPLOADING;

  @Column(name = "content_hash", length = 64)
  private String contentHash;

  @Column(name = "page_count")
  private Integer pageCount;

  @Column(name = "word_count")
  private Integer wordCount;

  @Column(name = "language", length = 10)
  private String language;

  // The documents table has an `embedding` column, deliberately left unmapped. Nothing ever
  // wrote it — retrieval embeds chunks, not whole documents — so every row is null, and
  // hibernate-vector throws a NullPointerException reading a null vector rather than yielding
  // null. Mapped, it made every query that loads a Document fail. Left unmapped it is inert;
  // dropping the column is a migration for whenever document-level similarity is ruled out.

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson = "{}";

  @Column(name = "processing_error", columnDefinition = "text")
  private String processingError;

  @Column(name = "processed_at")
  private Instant processedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_id")
  private User createdByUser;

  @Column(name = "created_by_id", insertable = false, updatable = false)
  private UUID createdById;

  @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<DocumentChunk> chunks = new ArrayList<>();

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public void setFileSize(Long fileSize) {
    this.fileSize = fileSize;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(String storagePath) {
    this.storagePath = storagePath;
  }

  public DocumentStatus getStatus() {
    return status;
  }

  public void setStatus(DocumentStatus status) {
    this.status = status;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public Integer getPageCount() {
    return pageCount;
  }

  public void setPageCount(Integer pageCount) {
    this.pageCount = pageCount;
  }

  public Integer getWordCount() {
    return wordCount;
  }

  public void setWordCount(Integer wordCount) {
    this.wordCount = wordCount;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }

  public String getProcessingError() {
    return processingError;
  }

  public void setProcessingError(String processingError) {
    this.processingError = processingError;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public void setProcessedAt(Instant processedAt) {
    this.processedAt = processedAt;
  }

  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  public User getCreatedByUser() {
    return createdByUser;
  }

  public void setCreatedByUser(User createdByUser) {
    this.createdByUser = createdByUser;
  }

  public UUID getCreatedById() {
    return createdById;
  }

  public void setCreatedById(UUID createdById) {
    this.createdById = createdById;
  }

  public List<DocumentChunk> getChunks() {
    return chunks;
  }

  public void setChunks(List<DocumentChunk> chunks) {
    this.chunks = chunks;
  }
}
