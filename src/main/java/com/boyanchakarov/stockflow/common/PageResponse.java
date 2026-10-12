package com.boyanchakarov.stockflow.common;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {
  public static <T> PageResponse<T> of(Page<T> p) {
    return new PageResponse<>(
        p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  public static <T> PageResponse<T> of(List<T> data, int page, int size, long total) {
    return new PageResponse<>(data, page, size, total, (int) ((total + size - 1) / size));
  }
}
