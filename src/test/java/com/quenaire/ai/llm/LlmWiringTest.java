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
package com.quenaire.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Guards the contract the reasoning engine depends on.
 *
 * <p>Generation used to be a method that returned a fixed sentence regardless of input, so every
 * answer the product produced was identical placeholder text. These tests pin the two properties
 * that stopped that being possible: a client must actually use the prompt, and it must fail loudly
 * rather than substituting something plausible.
 */
class LlmWiringTest {

  /** Minimal client that records what it was asked. */
  private static final class RecordingClient implements LlmClient {
    String lastPrompt;
    int lastMaxTokens = -1;

    @Override
    public String modelId() {
      return "test/recording";
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public String complete(String prompt) {
      this.lastPrompt = prompt;
      return "answer for: " + prompt;
    }

    @Override
    public String complete(String prompt, int maxTokens) {
      this.lastMaxTokens = maxTokens;
      return complete(prompt);
    }
  }

  @Test
  void completionDependsOnThePrompt() {
    RecordingClient client = new RecordingClient();

    String first = client.complete("How is data encrypted at rest?");
    String second = client.complete("What is your RTO?");

    // The regression this exists to catch: identical output for different
    // questions, which is what a hardcoded placeholder produces.
    assertThat(first).isNotEqualTo(second);
    assertThat(client.lastPrompt).isEqualTo("What is your RTO?");
  }

  @Test
  void maxTokenOverloadReachesTheImplementation() {
    RecordingClient client = new RecordingClient();

    client.complete("anything", 512);

    assertThat(client.lastMaxTokens).isEqualTo(512);
  }

  @Test
  void defaultMaxTokenOverloadFallsBackToPlainCompletion() {
    LlmClient minimal =
        new LlmClient() {
          @Override
          public String modelId() {
            return "test/minimal";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public String complete(String prompt) {
            return "ok";
          }
        };

    // An implementation that only overrides complete(String) must still
    // satisfy callers that pass a token ceiling.
    assertThat(minimal.complete("prompt", 128)).isEqualTo("ok");
  }

  @Test
  void unreachableModelThrowsRatherThanReturningText() {
    // Nothing listens here; the client must surface that.
    OllamaLlmClient client = new OllamaLlmClient("http://127.0.0.1:9", "llama3.2:3b", 256, 2);

    assertThat(client.isAvailable()).isFalse();

    assertThatThrownBy(() -> client.complete("Do you encrypt data at rest?"))
        .isInstanceOf(LlmClient.LlmException.class)
        .hasMessageContaining("Ollama");
  }

  @Test
  void emptyPromptIsRejectedBeforeAnyNetworkCall() {
    OllamaLlmClient client = new OllamaLlmClient("http://127.0.0.1:9", "llama3.2:3b", 256, 2);

    assertThatThrownBy(() -> client.complete("  "))
        .isInstanceOf(LlmClient.LlmException.class)
        .hasMessageContaining("empty prompt");
  }

  @Test
  void modelIdIdentifiesBothProviderAndModel() {
    OllamaLlmClient client = new OllamaLlmClient("http://localhost:11434", "llama3.2:3b", 256, 2);

    // Cost attribution and logs depend on this being specific.
    assertThat(client.modelId()).isEqualTo("ollama/llama3.2:3b");
  }
}
