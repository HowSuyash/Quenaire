/*
 * Copyright (c) 2026 Quenaire. All rights reserved.
 *
 * This file is proprietary and confidential. It is part of an unpublished work
 * owned by Quenaire and is protected by copyright and trade secret law.
 *
 * No licence, express or implied, is granted. No part of this file may be
 * copied, modified, distributed, disclosed, or used to create derivative works
 * without the prior written permission of Quenaire.
 *
 * Unauthorised copying or distribution of this file, via any medium, is
 * strictly prohibited.
 */
package com.quenaire.ai.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

@Service
public class DocumentParserService {

  private final Tika tika = new Tika();
  private final AutoDetectParser parser = new AutoDetectParser();

  public ParsedDocument parse(MultipartFile file) throws IOException, SAXException, TikaException {
    try (InputStream inputStream = file.getInputStream()) {
      Metadata metadata = new Metadata();
      metadata.set("resourceName", file.getOriginalFilename());
      metadata.set(Metadata.CONTENT_TYPE, file.getContentType());

      BodyContentHandler handler = new BodyContentHandler(-1);
      ParseContext context = new ParseContext();

      parser.parse(inputStream, handler, metadata, context);

      String content = handler.toString().trim();
      String mimeType = tika.detect(inputStream, file.getOriginalFilename());

      return new ParsedDocument(
          content,
          mimeType,
          metadata.get("title"),
          metadata.get("Author"),
          metadata.get("Creation-Date"),
          extractPageCount(metadata),
          countWords(content));
    }
  }

  public ParsedDocument parseText(String content, String mimeType) {
    return new ParsedDocument(content.trim(), mimeType, null, null, null, 1, countWords(content));
  }

  public ParsedDocument parse(Path filePath) throws IOException, SAXException, TikaException {
    try (InputStream inputStream = Files.newInputStream(filePath)) {
      Metadata metadata = new Metadata();
      metadata.set("resourceName", filePath.getFileName().toString());

      BodyContentHandler handler = new BodyContentHandler(-1);
      ParseContext context = new ParseContext();

      parser.parse(inputStream, handler, metadata, context);

      String content = handler.toString().trim();
      String mimeType = tika.detect(inputStream, filePath.getFileName().toString());

      return new ParsedDocument(
          content,
          mimeType,
          metadata.get("title"),
          metadata.get("Author"),
          metadata.get("Creation-Date"),
          extractPageCount(metadata),
          countWords(content));
    }
  }

  private Integer extractPageCount(Metadata metadata) {
    String pages = metadata.get("xmpTPg:NPages");
    if (pages == null) {
      pages = metadata.get("pdf:pageCount");
    }
    if (pages == null) {
      pages = metadata.get("page-count");
    }
    try {
      return pages != null ? Integer.parseInt(pages) : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Integer countWords(String content) {
    if (content == null || content.isBlank()) {
      return 0;
    }
    return content.split("\\s+").length;
  }

  public record ParsedDocument(
      String content,
      String mimeType,
      String title,
      String author,
      String creationDate,
      Integer pageCount,
      Integer wordCount) {}
}
