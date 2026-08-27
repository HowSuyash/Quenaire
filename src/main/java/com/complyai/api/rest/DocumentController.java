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
package com.complyai.api.rest;

import com.complyai.ai.ingestion.DocumentIngestionService;
import com.complyai.domain.document.Document;
import com.complyai.domain.document.DocumentRepository;
import com.complyai.domain.document.DocumentStatus;
import com.complyai.security.TenantContext;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

  private final DocumentIngestionService ingestionService;
  private final DocumentRepository documentRepository;

  public DocumentController(
      DocumentIngestionService ingestionService, DocumentRepository documentRepository) {
    this.ingestionService = ingestionService;
    this.documentRepository = documentRepository;
  }

  @PostMapping(consumes = "multipart/form-data")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
  public ResponseEntity<Document> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "sourceType", defaultValue = "UPLOAD") String sourceType)
      throws IOException {

    Document document = ingestionService.ingest(file, sourceType);
    return ResponseEntity.ok(document);
  }

  @PostMapping("/text")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
  public ResponseEntity<Document> ingestText(@RequestBody TextIngestRequest request) {

    Document document =
        ingestionService.ingestText(
            request.title(), request.content(), request.mimeType(), request.sourceType());
    return ResponseEntity.ok(document);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Page<Document>> list(
      @RequestParam(required = false) DocumentStatus status, Pageable pageable) {

    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    Page<Document> documents =
        (status != null)
            ? documentRepository.findByTenantIdAndStatusAndDeletedAtIsNull(
                tenantId, status, pageable)
            : documentRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);

    return ResponseEntity.ok(documents);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Document> get(@PathVariable UUID id) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return documentRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    return documentRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
        .map(
            doc -> {
              doc.setDeletedAt(java.time.Instant.now());
              documentRepository.save(doc);
              return ResponseEntity.noContent().<Void>build();
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/stats")
  @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')")
  public ResponseEntity<Map<String, Object>> stats() {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    long total = documentRepository.countByTenantIdAndDeletedAtIsNull(tenantId);
    long indexed =
        documentRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, DocumentStatus.INDEXED);
    long processing =
        documentRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, DocumentStatus.PROCESSING);
    long failed =
        documentRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, DocumentStatus.FAILED);

    return ResponseEntity.ok(
        Map.of(
            "total", total,
            "indexed", indexed,
            "processing", processing,
            "failed", failed));
  }

  public record TextIngestRequest(
      String title, String content, String mimeType, String sourceType) {}
}
