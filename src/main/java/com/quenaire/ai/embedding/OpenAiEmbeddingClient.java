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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
 * Hosted embedding, so a deployment needs no model server of its own.
 *
 * <p>Running Ollama purely to embed costs about a gigabyte of resident memory and re-downloads the
 * model on every cold start — for the cheap half of the pipeline. This removes that service
 * entirely; generation still chooses between hosted and local separately.
 *
 * <p>What this means for a customer: the text of their security documentation is sent to OpenAI to
 * be embedded. That is a statement about where their data goes and belongs in the contract and the
 * trust centre, not only in configuration. A customer who cannot accept it runs the Ollama client.
 *
 * <p>{@code dimensions} is requested explicitly. The model returns 1536 natively and the schema
 * declares {@code vector(768)}; asking for 768 truncates at the source, which this family of models
 * supports directly, so no migration is needed. Without it Postgres rejects every insert.
 */
@Component
@ConditionalOnProperty(name = "app.embeddings.provider", havingValue = "openai")
public class OpenAiEmbeddingClient implements EmbeddingClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

  private static final String API_URL = "https://api.openai.com";

  /** One request per document's worth of chunks rather than one per chunk. */
  private static final int MAX_BATCH = 96;

  private final RestClient client;
  private final String model;
  private final boolean configured;

  public OpenAiEmbeddingClient(
      @Value("${app.embeddings.openai.api-key:}") String apiKey,
      @Value("${app.embeddings.openai.model:text-embedding-3-small}") String model,
      @Value("${app.embeddings.openai.timeout-seconds:30}") long timeoutSeconds) {

    this.model = model;
    this.configured = apiKey != null && !apiKey.isBlank();

    if (!configured) {
      // Not a startup failure: ingestion needs this, but review, export and the rest
      // of the product do not, and refusing to boot would take all of it down.
      log.warn("app.embeddings.openai.api-key is not set; documents cannot be indexed");
    }

    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(10));
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

    this.client =
        RestClient.builder()
            .baseUrl(API_URL)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (configured ? apiKey : "unset"))
            .build();
  }

  @Override
  public String modelId() {
    return "openai/" + model;
  }

  @Override
  public int dimensions() {
    return VectorLiterals.DIMENSIONS;
  }

  /** Reports on configuration, not reachability: a probe would be billed. */
  @Override
  public boolean isAvailable() {
    return configured;
  }

  @Override
  public float[] embed(String text) {
    return embedBatch(List.of(text)).get(0);
  }

  @Override
  public List<float[]> embedBatch(List<String> texts) {
    if (texts.isEmpty()) {
      return List.of();
    }
    if (!configured) {
      throw new EmbeddingException("No OpenAI API key configured");
    }

    List<float[]> all = new ArrayList<>(texts.size());
    for (int from = 0; from < texts.size(); from += MAX_BATCH) {
      all.addAll(requestBatch(texts.subList(from, Math.min(from + MAX_BATCH, texts.size()))));
    }
    return all;
  }

  private List<float[]> requestBatch(List<String> batch) {
    long started = System.currentTimeMillis();
    try {
      JsonNode response =
          client
              .post()
              .uri("/v1/embeddings")
              .body(
                  Map.of(
                      "model", model,
                      "input", batch,
                      "dimensions", VectorLiterals.DIMENSIONS))
              .retrieve()
              .body(JsonNode.class);

      JsonNode data = response == null ? null : response.get("data");
      if (data == null || !data.isArray() || data.size() != batch.size()) {
        throw new EmbeddingException(
            "OpenAI returned "
                + (data == null ? "no data" : data.size() + " vectors")
                + " for "
                + batch.size()
                + " inputs");
      }

      List<float[]> vectors = new ArrayList<>(batch.size());
      for (JsonNode item : data) {
        JsonNode embedding = item.get("embedding");
        if (embedding == null || embedding.size() != VectorLiterals.DIMENSIONS) {
          throw new EmbeddingException(
              "Expected "
                  + VectorLiterals.DIMENSIONS
                  + "-dimensional vectors, got "
                  + (embedding == null ? "none" : String.valueOf(embedding.size())));
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
          vector[i] = embedding.get(i).floatValue();
        }
        vectors.add(vector);
      }

      // Tokens are the unit cost of indexing a document; logged from the first call.
      JsonNode usage = response.get("usage");
      log.debug(
          "OpenAI {} embedded {} inputs in {}ms ({} tokens)",
          model,
          batch.size(),
          System.currentTimeMillis() - started,
          usage == null ? "?" : usage.get("total_tokens"));

      return vectors;
    } catch (EmbeddingException e) {
      throw e;
    } catch (Exception e) {
      throw new EmbeddingException("OpenAI embedding failed for model " + model, e);
    }
  }
}
