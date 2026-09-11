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
package com.quenaire.ai.rag;

/** Rendering of embeddings for pgvector query parameters. */
public final class VectorLiterals {

  /**
   * Dimensionality of the embedding model in use (nomic-embed-text).
   *
   * <p>Must agree with the {@code vector(n)} columns; Postgres rejects a mismatch outright. Kept
   * here so entities and tests share one value rather than repeating a literal.
   */
  public static final int DIMENSIONS = 768;

  private VectorLiterals() {}

  /**
   * pgvector's text form: {@code [0.1,0.2,...]}.
   *
   * <p>Native queries pass the vector as text and cast it in SQL rather than binding a driver type.
   * Binding is where this previously went wrong -- an unrecognised type is serialised to bytea,
   * which Postgres refuses against a vector column.
   */
  public static String of(float[] vector) {
    StringBuilder sb = new StringBuilder(vector.length * 8 + 2).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(vector[i]);
    }
    return sb.append(']').toString();
  }
}
