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
package com.complyai.ai.ingestion;

import com.complyai.domain.document.Document;
import com.complyai.domain.document.DocumentChunk;
import com.complyai.domain.document.DocumentChunkRepository;
import com.complyai.domain.document.DocumentRepository;
import com.complyai.domain.document.DocumentStatus;
import com.complyai.security.TenantContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

@Service
public class DocumentIngestionService {

  private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

  private final DocumentRepository documentRepository;
  private final DocumentChunkRepository chunkRepository;
  private final DocumentParserService parserService;
  private final DocumentChunkerService chunkerService;
  private final EmbeddingService embeddingService;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final String storagePath;
  private final com.complyai.domain.tenant.TenantRepository tenantRepository;

  public DocumentIngestionService(
      DocumentRepository documentRepository,
      DocumentChunkRepository chunkRepository,
      DocumentParserService parserService,
      DocumentChunkerService chunkerService,
      EmbeddingService embeddingService,
      KafkaTemplate<String, Object> kafkaTemplate,
      @Value("${app.storage.local.path:./storage}") String storagePath,
      com.complyai.domain.tenant.TenantRepository tenantRepository) {
    this.documentRepository = documentRepository;
    this.chunkRepository = chunkRepository;
    this.parserService = parserService;
    this.chunkerService = chunkerService;
    this.embeddingService = embeddingService;
    this.kafkaTemplate = kafkaTemplate;
    this.storagePath = storagePath;
    this.tenantRepository = tenantRepository;
  }

  @Transactional
  public Document ingest(MultipartFile file, String sourceType) throws IOException {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    String contentHash = computeHash(file);
    var existing =
        documentRepository.findByTenantIdAndContentHashAndDeletedAtIsNull(tenantId, contentHash);
    if (existing.isPresent()) {
      throw new DuplicateDocumentException("Document with same content already exists");
    }

    Document document = new Document();
    document.setTitle(file.getOriginalFilename());
    document.setSourceType(sourceType);
    document.setFileName(file.getOriginalFilename());
    document.setMimeType(file.getContentType());
    document.setFileSize(file.getSize());
    document.setContentHash(contentHash);
    document.setStatus(DocumentStatus.UPLOADING);
    document.setTenant(tenantRepository.getReferenceById(tenantId));
    document.setCreatedById(getCurrentUserId());

    String storageFileName = tenantId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
    Path storagePath = Paths.get(this.storagePath, storageFileName);
    Files.createDirectories(storagePath.getParent());
    file.transferTo(storagePath);
    document.setStoragePath(storagePath.toString());

    document = documentRepository.save(document);

    try {
      processDocument(document, file);
    } catch (Exception e) {
      document.setStatus(DocumentStatus.FAILED);
      document.setProcessingError(e.getMessage());
      documentRepository.save(document);
      throw new IngestionException("Failed to process document", e);
    }

    return document;
  }

  @Transactional
  public Document ingestText(String title, String content, String mimeType, String sourceType) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    String contentHash = computeHash(content);
    var existing =
        documentRepository.findByTenantIdAndContentHashAndDeletedAtIsNull(tenantId, contentHash);
    if (existing.isPresent()) {
      throw new DuplicateDocumentException("Document with same content already exists");
    }

    Document document = new Document();
    document.setTitle(title);
    document.setSourceType(sourceType);
    document.setMimeType(mimeType);
    document.setContentHash(contentHash);
    document.setStatus(DocumentStatus.UPLOADING);
    document.setTenant(tenantRepository.getReferenceById(tenantId));
    document.setCreatedById(getCurrentUserId());
    document.setWordCount(countWords(content));

    document = documentRepository.save(document);

    try {
      processTextDocument(document, content);
    } catch (Exception e) {
      document.setStatus(DocumentStatus.FAILED);
      document.setProcessingError(e.getMessage());
      documentRepository.save(document);
      throw new IngestionException("Failed to process document", e);
    }

    return document;
  }

  private void processDocument(Document document, MultipartFile file)
      throws IOException, SAXException, TikaException {
    document.setStatus(DocumentStatus.PROCESSING);
    documentRepository.save(document);

    var parsed = parserService.parse(file);
    document.setPageCount(parsed.pageCount());
    document.setWordCount(parsed.wordCount());
    document.setLanguage(detectLanguage(parsed.content()));

    List<DocumentChunkerService.DocumentChunk> chunks =
        chunkerService.chunk(document.getId().toString(), parsed.content());

    for (int i = 0; i < chunks.size(); i++) {
      var chunk = chunks.get(i);
      float[] embedding = embeddingService.embed(chunk.content());

      DocumentChunk docChunk = new DocumentChunk();
      docChunk.setDocument(document);
      docChunk.setContent(chunk.content());
      docChunk.setChunkIndex(chunk.chunkIndex());
      docChunk.setTokenCount(chunk.tokenCount());
      docChunk.setStartChar(chunk.startChar());
      docChunk.setEndChar(chunk.endChar());
      docChunk.setEmbedding(embedding);
      docChunk.setTenant(document.getTenant());

      chunkRepository.save(docChunk);
    }

    document.setStatus(DocumentStatus.INDEXED);
    document.setProcessedAt(Instant.now());
    documentRepository.save(document);

    publishIndexed(document);
  }

  private void processTextDocument(Document document, String content) {
    document.setStatus(DocumentStatus.PROCESSING);
    documentRepository.save(document);

    var parsed = parserService.parseText(content, document.getMimeType());
    document.setWordCount(parsed.wordCount());
    document.setLanguage(detectLanguage(content));

    List<DocumentChunkerService.DocumentChunk> chunks =
        chunkerService.chunk(document.getId().toString(), content);

    for (var chunk : chunks) {
      float[] embedding = embeddingService.embed(chunk.content());

      DocumentChunk docChunk = new DocumentChunk();
      docChunk.setDocument(document);
      docChunk.setContent(chunk.content());
      docChunk.setChunkIndex(chunk.chunkIndex());
      docChunk.setTokenCount(chunk.tokenCount());
      docChunk.setStartChar(chunk.startChar());
      docChunk.setEndChar(chunk.endChar());
      docChunk.setEmbedding(embedding);
      docChunk.setTenant(document.getTenant());

      chunkRepository.save(docChunk);
    }

    document.setStatus(DocumentStatus.INDEXED);
    document.setProcessedAt(Instant.now());
    documentRepository.save(document);

    publishIndexed(document);
  }

  private String computeHash(MultipartFile file) throws IOException {
    try (var inputStream = file.getInputStream()) {
      return computeHash(inputStream);
    }
  }

  private String computeHash(String content) {
    return computeHash(content.getBytes());
  }

  private String computeHash(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to compute hash", e);
    }
  }

  private String computeHash(java.io.InputStream inputStream) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        digest.update(buffer, 0, bytesRead);
      }
      byte[] hash = digest.digest();
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to compute hash", e);
    }
  }

  private String detectLanguage(String content) {
    return "en";
  }

  private int countWords(String content) {
    if (content == null || content.isBlank()) return 0;
    return content.split("\\s+").length;
  }

  private UUID getCurrentUserId() {
    return null;
  }

  public static class IngestionException extends RuntimeException {
    public IngestionException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class DuplicateDocumentException extends RuntimeException {
    public DuplicateDocumentException(String message) {
      super(message);
    }
  }

  /**
   * Announce that a document is indexed, without letting the announcement undo the work.
   *
   * <p>By the time this runs the document is parsed, chunked, embedded and saved. Letting a broker
   * outage propagate meant the caller marked a perfectly good document FAILED and the user's upload
   * was rejected -- so an unavailable Kafka stopped all ingestion.
   */
  private void publishIndexed(Document document) {
    try {
      kafkaTemplate.send("document.indexed", document.getId().toString(), document);
    } catch (Exception e) {
      log.warn(
          "Document {} is indexed but the document.indexed event could not be published: {}",
          document.getId(),
          e.getMessage());
    }
  }
}
