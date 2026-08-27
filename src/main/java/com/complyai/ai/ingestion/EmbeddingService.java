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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingService {

  private final RestClient restClient;
  private final String embeddingModel;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public EmbeddingService(
      @Value("${app.ollama.base-url:http://localhost:11434}") String baseUrl,
      @Value("${app.ollama.embedding-model:nomic-embed-text}") String embeddingModel) {
    this.restClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    this.embeddingModel = embeddingModel;
  }

  /** The configured embedding model. Recorded alongside stored vectors. */
  public String modelId() {
    return embeddingModel;
  }

  public float[] embed(String text) {
    try {
      Map<String, Object> request =
          Map.of(
              "model", embeddingModel,
              "prompt", text);

      JsonNode response =
          restClient.post().uri("/api/embeddings").body(request).retrieve().body(JsonNode.class);

      JsonNode embeddingNode = response.get("embedding");
      if (embeddingNode == null || !embeddingNode.isArray()) {
        throw new EmbeddingException("Invalid embedding response: " + response);
      }

      float[] result = new float[embeddingNode.size()];
      for (int i = 0; i < embeddingNode.size(); i++) {
        result[i] = embeddingNode.get(i).floatValue();
      }
      return result;
    } catch (Exception e) {
      throw new EmbeddingException("Failed to generate embedding", e);
    }
  }

  public List<float[]> embedBatch(List<String> texts) {
    return texts.stream().map(this::embed).toList();
  }

  public static class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message, Throwable cause) {
      super(message, cause);
    }

    public EmbeddingException(String message) {
      super(message);
    }
  }
}
