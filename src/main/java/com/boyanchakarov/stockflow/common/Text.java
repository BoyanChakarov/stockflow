package com.boyanchakarov.stockflow.common;

import java.util.Locale;

public final class Text {
  private Text() {}

  public static String trim(String value) {
    return value == null ? null : value.strip();
  }

  public static String optional(String value) {
    String v = trim(value);
    return v == null || v.isEmpty() ? null : v;
  }

  public static String sku(String value) {
    return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
  }

  public static String username(String value) {
    return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
  }
}
