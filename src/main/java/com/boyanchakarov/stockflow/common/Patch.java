package com.boyanchakarov.stockflow.common;

import java.math.BigDecimal;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Presence-aware JSON object: missing preserves, null clears only optional fields. */
public final class Patch {
  private final JsonNode fields;

  public Patch(JsonNode fields, Set<String> allowed) {
    if (fields == null || !fields.isObject() || fields.isEmpty())
      throw BusinessException.invalid("A nonempty patch object is required.");
    for (String name : fields.propertyNames())
      if (!allowed.contains(name))
        throw BusinessException.invalid("Unknown or immutable field: " + name);
    this.fields = fields;
  }

  public boolean has(String key) {
    return fields.has(key);
  }

  public String text(String key, String old, boolean required) {
    if (!has(key)) return old;
    JsonNode v = fields.get(key);
    if (v.isNull() && !required) return null;
    if (!v.isString())
      throw BusinessException.invalid(
          key + " must be a string" + (required ? " and cannot be null." : " or null."));
    return required ? Text.trim(v.asString()) : Text.optional(v.asString());
  }

  public BigDecimal price(String key, BigDecimal old) {
    if (!has(key)) return old;
    JsonNode v = fields.get(key);
    if (!v.isString() && !v.isNumber())
      throw BusinessException.invalid(key + " must be a decimal.");
    try {
      return new BigDecimal(v.asString());
    } catch (NumberFormatException e) {
      throw BusinessException.invalid(key + " must be a decimal.");
    }
  }

  public long number(String key, long old) {
    if (!has(key)) return old;
    JsonNode v = fields.get(key);
    if (!v.isIntegralNumber() || !v.canConvertToLong())
      throw BusinessException.invalid(key + " must be a whole number.");
    return v.longValue();
  }

  public boolean bool(String key, boolean old) {
    if (!has(key)) return old;
    JsonNode v = fields.get(key);
    if (!v.isBoolean()) throw BusinessException.invalid(key + " must be a boolean.");
    return v.booleanValue();
  }
}
