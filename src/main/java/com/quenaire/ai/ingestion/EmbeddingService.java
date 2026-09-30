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

import com.quenaire.ai.embedding.EmbeddingClient;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Embedding, as the rest of the application sees it.
 *
 * <p>Delegates to whichever {@link EmbeddingClient} is configured. This class used to hold the
 * Ollama HTTP call itself, so every deployment needed a model server running beside it whether it
 * wanted one or not -- and each chunk cost its own round trip, because there was nowhere for a
 * provider to say it could take a batch.
 */
@Service
public class EmbeddingService {

  private final EmbeddingClient client;

  public EmbeddingService(EmbeddingClient client) {
    this.client = client;
  }

  /** The model that produced these vectors. Recorded alongside them. */
  public String modelId() {
    return client.modelId();
  }

  public float[] embed(String text) {
    return client.embed(text);
  }

  /** One request where the provider supports it, rather than one per text. */
  public List<float[]> embedBatch(List<String> texts) {
    return client.embedBatch(texts);
  }

  /** Kept so existing callers still compile; the client defines it now. */
  public static class EmbeddingException extends EmbeddingClient.EmbeddingException {
    public EmbeddingException(String message, Throwable cause) {
      super(message, cause);
    }

    public EmbeddingException(String message) {
      super(message);
    }
  }
}
