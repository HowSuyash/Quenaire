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

import java.time.Duration;
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

/**
 * Hosted generation through Anthropic's Messages API.
 *
 * <p>The tier split this exists for: a self-hosted customer keeps their documentation inside their
 * own network with {@link OllamaLlmClient}, and pays for it in latency — measured at roughly 20
 * seconds a question on CPU, which is half an hour for a ninety-question questionnaire. The hosted
 * tier sends prompt excerpts to Anthropic and answers in seconds.
 *
 * <p>That difference is not a detail to bury. Prompts contain excerpts of a customer's security
 * documentation, so which tier a customer is on is a statement about where their data goes, and
 * belongs in the contract and the trust centre rather than only in configuration.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "anthropic")
public class AnthropicLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

  private static final String API_URL = "https://api.anthropic.com";
  private static final String API_VERSION = "2023-06-01";

  /** Matches {@link OllamaLlmClient}: compliance answers should be close to reproducible. */
  private static final double TEMPERATURE = 0.1;

  private final RestClient client;
  private final String model;
  private final int defaultMaxTokens;
  private final boolean configured;

  public AnthropicLlmClient(
      @Value("${app.anthropic.api-key:}") String apiKey,
      @Value("${app.anthropic.model:claude-haiku-4-5-20251001}") String model,
      @Value("${app.anthropic.max-tokens:2048}") int defaultMaxTokens,
      @Value("${app.anthropic.timeout-seconds:120}") long timeoutSeconds) {

    this.model = model;
    this.defaultMaxTokens = defaultMaxTokens;
    this.configured = apiKey != null && !apiKey.isBlank();

    if (!configured) {
      // Deliberately not a startup failure: the rest of the product -- ingestion, review,
      // export -- works without generation, and refusing to boot would take all of it down.
      log.warn("app.anthropic.api-key is not set; generation will be unavailable");
    }

    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(10));
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

    this.client =
        RestClient.builder()
            .baseUrl(API_URL)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("x-api-key", configured ? apiKey : "unset")
            .defaultHeader("anthropic-version", API_VERSION)
            .build();
  }

  @Override
  public String modelId() {
    return "anthropic/" + model;
  }

  /**
   * Whether generation can be attempted.
   *
   * <p>Reports on configuration rather than reachability. The obvious alternative -- a probe
   * request -- is billed, and this is called before every question.
   */
  @Override
  public boolean isAvailable() {
    return configured;
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
    if (!configured) {
      throw new LlmException("No Anthropic API key configured");
    }

    long started = System.currentTimeMillis();

    try {
      Map<String, Object> request =
          Map.of(
              "model", model,
              "max_tokens", maxTokens,
              "temperature", TEMPERATURE,
              "messages", List.of(Map.of("role", "user", "content", prompt)));

      Map<String, Object> response =
          client.post().uri("/v1/messages").body(request).retrieve().body(Map.class);

      if (response == null) {
        throw new LlmException("Anthropic returned an empty response body");
      }

      String text = firstTextBlock(response);
      if (text == null || text.isBlank()) {
        throw new LlmException("Anthropic returned no completion for model " + model);
      }

      // Token counts are the unit cost of an answer, and answers-per-rupee decides
      // whether the pricing works. Logged from the first call rather than reconstructed.
      Map<String, Object> usage = (Map<String, Object>) response.get("usage");
      log.debug(
          "Anthropic {} completed in {}ms (in {} tokens, out {} tokens)",
          model,
          System.currentTimeMillis() - started,
          usage == null ? "?" : usage.get("input_tokens"),
          usage == null ? "?" : usage.get("output_tokens"));

      return text.trim();

    } catch (LlmException e) {
      throw e;
    } catch (Exception e) {
      throw new LlmException("Anthropic generation failed for model " + model, e);
    }
  }

  /** The Messages API returns content as a list of typed blocks; we send text and want text. */
  @SuppressWarnings("unchecked")
  private static String firstTextBlock(Map<String, Object> response) {
    Object content = response.get("content");
    if (!(content instanceof List<?> blocks)) {
      return null;
    }
    for (Object block : blocks) {
      if (block instanceof Map<?, ?> map && "text".equals(map.get("type"))) {
        return (String) ((Map<String, Object>) map).get("text");
      }
    }
    return null;
  }
}
