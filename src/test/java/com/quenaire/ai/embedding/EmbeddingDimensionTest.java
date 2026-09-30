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

import static org.assertj.core.api.Assertions.assertThat;

import com.quenaire.ai.rag.VectorLiterals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every embedding provider has to agree with the schema.
 *
 * <p>The {@code embedding} columns are {@code vector(768)} and Postgres refuses anything else
 * outright — that is how the first version of this pipeline failed, storing nothing for weeks
 * because the schema said 1536 and the model produced 768. A provider added later that quietly
 * returns a different width would do it again, so the agreement is asserted rather than assumed.
 */
class EmbeddingDimensionTest {

  @Test
  @DisplayName("the hosted client asks for the width the schema declares")
  void hostedMatchesTheSchema() {
    var client = new OpenAiEmbeddingClient("", "text-embedding-3-small", 30);

    assertThat(client.dimensions()).isEqualTo(VectorLiterals.DIMENSIONS);
  }

  @Test
  @DisplayName("the self-hosted client asks for the same width")
  void selfHostedMatchesTheSchema() {
    var client = new OllamaEmbeddingClient("http://localhost:11434", "nomic-embed-text");

    assertThat(client.dimensions()).isEqualTo(VectorLiterals.DIMENSIONS);
  }

  @Test
  @DisplayName("a client with no key reports itself unavailable rather than failing at startup")
  void unconfiguredIsNotFatal() {
    // Ingestion needs a key; review, export and the rest of the product do not. Refusing
    // to start would take all of it down over one unset value.
    var client = new OpenAiEmbeddingClient("", "text-embedding-3-small", 30);

    assertThat(client.isAvailable()).isFalse();
  }

  @Test
  @DisplayName("embedding without a key says so instead of returning a wrong vector")
  void unconfiguredRefusesToEmbed() {
    var client = new OpenAiEmbeddingClient("", "text-embedding-3-small", 30);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.embed("anything"))
        .isInstanceOf(EmbeddingClient.EmbeddingException.class)
        .hasMessageContaining("No OpenAI API key");
  }

  @Test
  @DisplayName("an empty batch costs nothing")
  void emptyBatchMakesNoRequest() {
    var client = new OpenAiEmbeddingClient("sk-not-a-real-key", "text-embedding-3-small", 30);

    assertThat(client.embedBatch(java.util.List.of())).isEmpty();
  }
}
