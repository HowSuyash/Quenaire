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

/**
 * A text-completion model, behind one interface.
 *
 * <p>This is the seam where the hosting model is chosen. The cloud tier wants the strongest
 * available model because answer quality is the product; the self-hosted tier wants a local model
 * because the pitch is that customer data never leaves their infrastructure. Those are different
 * implementations of this interface, not different codebases.
 *
 * <p>Implementations must be safe to call concurrently.
 */
public interface LlmClient {

  /** Name of the model actually serving requests, for logging and cost attribution. */
  String modelId();

  /** True when the client has everything it needs to answer. */
  boolean isAvailable();

  /**
   * Complete a prompt.
   *
   * @throws LlmException when the model cannot be reached or returns nothing. Callers must not
   *     paper over this — a questionnaire answer that silently becomes placeholder text is worse
   *     than a visible failure.
   */
  String complete(String prompt);

  /**
   * Complete a prompt with an explicit token ceiling, for callers that need a short answer and do
   * not want to pay for a long one.
   */
  default String complete(String prompt, int maxTokens) {
    return complete(prompt);
  }

  class LlmException extends RuntimeException {
    public LlmException(String message) {
      super(message);
    }

    public LlmException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
