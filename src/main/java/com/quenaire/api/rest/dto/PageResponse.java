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
package com.quenaire.api.rest.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * One shape for every paged response.
 *
 * <p>There were three. The questionnaire listing returned {@code items}; the document listing
 * returned a Spring {@code Page}, which serialises the page index as {@code number}; and the
 * client's own {@code PaginatedResponse} type declared {@code content} and {@code page}. So two of
 * the three disagreed with what the client was written to read.
 *
 * <p>Spring's {@code Page} is deliberately not returned directly: its JSON is the serialisation of
 * an internal Spring type, which means the wire format changes when Spring changes it.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {

  /** Maps a page of entities into a page of responses. */
  public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }
}
