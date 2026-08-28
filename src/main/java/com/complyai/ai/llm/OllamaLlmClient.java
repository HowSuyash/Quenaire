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
package com.complyai.ai.llm;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Self-hosted generation through Ollama.
 *
 * <p>This is the data-sovereignty path: the prompt, which contains excerpts of the customer's
 * security documentation, never leaves their network.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(OllamaLlmClient.class);

  /**
   * Low but non-zero. Compliance answers should be reproducible; a little variation still helps the
   * model recover from an awkward phrasing.
   */
  private static final double TEMPERATURE = 0.1;

  private final RestClient client;
  private final String model;
  private final int defaultMaxTokens;
  private final String baseUrl;

  public OllamaLlmClient(
      @Value("${app.ollama.base-url:http://localhost:11434}") String baseUrl,
      @Value("${app.ollama.model:llama3.1:8b}") String model,
      @Value("${app.ollama.max-tokens:2048}") int defaultMaxTokens,
      @Value("${app.ollama.timeout-seconds:120}") long timeoutSeconds) {

    this.baseUrl = baseUrl;
    this.model = model;
    this.defaultMaxTokens = defaultMaxTokens;

    // Generation on a local model is slow; the default client timeout would
    // abort a perfectly healthy request part-way through a long answer.
    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(10));
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

    this.client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public String modelId() {
    return "ollama/" + model;
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
  public String complete(String prompt) {
    return complete(prompt, defaultMaxTokens);
  }

  @Override
  @SuppressWarnings("unchecked")
  public String complete(String prompt, int maxTokens) {
    if (prompt == null || prompt.isBlank()) {
      throw new LlmException("Refusing to send an empty prompt");
    }

    long started = System.currentTimeMillis();

    try {
      Map<String, Object> request =
          Map.of(
              "model",
              model,
              "prompt",
              prompt,
              "stream",
              false,
              "options",
              Map.of(
                  "num_predict", maxTokens,
                  "temperature", TEMPERATURE));

      Map<String, Object> response =
          client.post().uri("/api/generate").body(request).retrieve().body(Map.class);

      if (response == null) {
        throw new LlmException("Ollama returned an empty response body");
      }

      String text = (String) response.get("response");
      if (text == null || text.isBlank()) {
        throw new LlmException("Ollama returned no completion for model " + model);
      }

      // These numbers drive the pricing floor, so they belong in the log
      // from day one rather than being reconstructed later.
      log.debug(
          "Ollama {} completed in {}ms (prompt {} chars, {} eval tokens)",
          model,
          System.currentTimeMillis() - started,
          prompt.length(),
          response.getOrDefault("eval_count", "?"));

      return text.trim();

    } catch (LlmException e) {
      throw e;
    } catch (Exception e) {
      throw new LlmException("Ollama request failed for model " + model + " at " + baseUrl, e);
    }
  }
}
