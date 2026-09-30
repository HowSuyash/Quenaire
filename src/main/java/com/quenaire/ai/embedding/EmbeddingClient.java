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

/**
 * Turns text into the vector stored beside it in pgvector.
 *
 * <p>The same seam generation has, and for the same reason: a self-hosted deployment keeps the
 * customer's documentation inside their own network, and a hosted one trades that for not running a
 * model. Generation had this from the start; embedding called Ollama directly, which meant a
 * deployment could not exist without a model server — for the cheap half of the pipeline, at that.
 * Embedding a chunk takes about 90ms; generating an answer takes about twenty seconds.
 *
 * <p>Implementations must agree on dimensionality. The {@code embedding} columns are declared
 * {@code vector(768)} and Postgres rejects anything else outright, so a provider that returns a
 * different width needs a migration, not a configuration change.
 */
public interface EmbeddingClient {

  /** Identifies the model, recorded alongside the vectors it produced. */
  String modelId();

  /** Width of the vectors this client returns. Must match the schema's {@code vector(n)}. */
  int dimensions();

  /** Whether this client is configured well enough to be attempted. */
  boolean isAvailable();

  float[] embed(String text);

  /**
   * Embeds several texts.
   *
   * <p>Default is one call each. Hosted providers accept a batch in one request and should override
   * — ingestion embeds every chunk of a document, so the difference is a round trip per chunk
   * against one for the lot.
   */
  default java.util.List<float[]> embedBatch(java.util.List<String> texts) {
    return texts.stream().map(this::embed).toList();
  }

  /** Thrown when a vector could not be produced. */
  class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message) {
      super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
