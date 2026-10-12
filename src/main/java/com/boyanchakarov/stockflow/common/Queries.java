package com.boyanchakarov.stockflow.common;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

public final class Queries {
  private Queries() {}

  public static Pageable page(
      int page, int size, String sort, Set<String> allowed, String fallback) {
    if (page < 0 || page > 1000000 || size < 1 || size > 100)
      throw BusinessException.invalid("Page must be 0–1000000; size must be 1–100.");
    String[] parts = (sort == null ? fallback : sort).split(",", -1);
    if (parts.length > 2
        || !allowed.contains(parts[0])
        || (parts.length == 2 && !Set.of("asc", "desc").contains(parts[1])))
      throw BusinessException.invalid("Unsupported sort.");
    Sort.Direction direction =
        parts.length == 2 ? Sort.Direction.fromString(parts[1]) : Sort.Direction.ASC;
    return PageRequest.of(page, size, Sort.by(direction, parts[0]).and(Sort.by("id")));
  }

  public static String search(String q) {
    q = Text.optional(q);
    if (q != null && q.length() > 100)
      throw BusinessException.invalid("Search is limited to 100 characters.");
    return q;
  }

  public static void dates(Instant from, Instant to) {
    if (from != null && to != null && !from.isBefore(to))
      throw BusinessException.invalid("from must be before to.");
  }

  public static <T> Specification<T> equal(String field, Object value) {
    return (r, q, b) -> value == null ? b.conjunction() : b.equal(r.get(field), value);
  }

  public static <T> Specification<T> dateRange(Instant from, Instant to) {
    dates(from, to);
    return (r, q, b) ->
        b.and(
            from == null ? b.conjunction() : b.greaterThanOrEqualTo(r.get("createdAt"), from),
            to == null ? b.conjunction() : b.lessThan(r.get("createdAt"), to));
  }

  public static <T> Specification<T> searchFields(String text, String... fields) {
    String value = search(text);
    return (r, q, b) -> {
      if (value == null) return b.conjunction();
      String pattern =
          "%"
              + value
                  .toLowerCase(Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%";
      return b.or(
          Arrays.stream(fields)
              .map(f -> b.like(b.lower(r.get(f)), pattern, '\\'))
              .toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }
}
