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
package com.quenaire.ai.embedding;

import com.quenaire.ai.rag.VectorLiterals;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Self-hosted embedding through Ollama.
 *
 * <p>The data-sovereignty path: the text being embedded is the customer's security documentation,
 * and here it never leaves their network.
 */
@Component
@ConditionalOnProperty(
    name = "app.embeddings.provider",
    havingValue = "ollama",
    matchIfMissing = true)
public class OllamaEmbeddingClient implements EmbeddingClient {

  private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingClient.class);

  private final RestClient client;
  private final String model;
  private final String baseUrl;

  public OllamaEmbeddingClient(
      @Value("${app.ollama.base-url:http://localhost:11434}") String baseUrl,
      @Value("${app.ollama.embedding-model:nomic-embed-text}") String model) {
    this.baseUrl = baseUrl;
    this.model = model;
    this.client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public String modelId() {
    return model;
  }

  @Override
  public int dimensions() {
    return VectorLiterals.DIMENSIONS;
  }

  @Override
  public boolean isAvailable() {
    try {
      client.get().uri("/api/tags").retrieve().body(Map.class);
      return true;
    } catch (Exception e) {
      log.debug("Ollama not reachable at {}: {}", baseUrl, e.getMessage());
      return false;
    }
  }

  @Override
  public float[] embed(String text) {
    try {
      JsonNode response =
          client
              .post()
              .uri("/api/embeddings")
              .body(Map.of("model", model, "prompt", text))
              .retrieve()
              .body(JsonNode.class);

      JsonNode embedding = response == null ? null : response.get("embedding");
      if (embedding == null || !embedding.isArray()) {
        throw new EmbeddingException("Ollama returned no embedding for model " + model);
      }

      float[] result = new float[embedding.size()];
      for (int i = 0; i < embedding.size(); i++) {
        result[i] = embedding.get(i).floatValue();
      }
      return result;
    } catch (EmbeddingException e) {
      throw e;
    } catch (Exception e) {
      throw new EmbeddingException("Ollama embedding failed for model " + model, e);
    }
  }
}
