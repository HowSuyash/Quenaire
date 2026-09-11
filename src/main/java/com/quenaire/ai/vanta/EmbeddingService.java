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
package com.quenaire.ai.vanta;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Embeddings for the questionnaire pipeline.
 *
 * <p>Delegates to {@link com.quenaire.ai.ingestion.EmbeddingService}, which calls the configured
 * embedding model. This class used to return a hardcoded 5-element vector, which meant every
 * similarity score in the product was meaningless.
 *
 * <p>It exists as a thin adapter rather than being deleted because this package works in {@code
 * List<Float>} while ingestion works in {@code float[]}; the conversion belongs in one place.
 */
@Service("vantaEmbeddingService")
public class EmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

  private final com.quenaire.ai.ingestion.EmbeddingService delegate;

  public EmbeddingService(com.quenaire.ai.ingestion.EmbeddingService delegate) {
    this.delegate = delegate;
  }

  /**
   * Embed text with the configured model.
   *
   * @throws com.quenaire.ai.ingestion.EmbeddingService.EmbeddingException when the model is
   *     unreachable. Callers must not substitute a placeholder vector — a wrong embedding produces
   *     confident, irrelevant citations, which is worse than no answer.
   */
  public List<Float> generateEmbedding(String text) {
    float[] vector = delegate.embed(text);

    List<Float> boxed = new ArrayList<>(vector.length);
    for (float value : vector) {
      boxed.add(value);
    }

    log.debug("Embedded {} chars into {} dimensions", text.length(), boxed.size());
    return boxed;
  }

  /** Identifies the model behind these vectors, so a model change is detectable. */
  public String modelId() {
    return delegate.modelId();
  }

  public float[] generateEmbeddingRaw(String text) {
    return delegate.embed(text);
  }

  public List<List<Float>> generateEmbeddings(List<String> texts) {
    List<List<Float>> results = new ArrayList<>(texts.size());
    for (float[] vector : delegate.embedBatch(texts)) {
      List<Float> boxed = new ArrayList<>(vector.length);
      for (float value : vector) {
        boxed.add(value);
      }
      results.add(boxed);
    }
    return results;
  }
}
