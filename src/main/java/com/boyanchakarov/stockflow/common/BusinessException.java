package com.boyanchakarov.stockflow.common;

import java.util.Map;

public class BusinessException extends RuntimeException {
  private final int status;
  private final String code;
  private final Map<String, Object> properties;

  public BusinessException(int status, String code, String detail) {
    this(status, code, detail, Map.of());
  }

  public BusinessException(int status, String code, String detail, Map<String, Object> properties) {
    super(detail);
    this.status = status;
    this.code = code;
    this.properties = properties;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Map<String, Object> properties() {
    return properties;
  }

  public static BusinessException missing(String resource) {
    return new BusinessException(404, "NOT_FOUND", resource + " was not found.");
  }

  public static BusinessException invalid(String detail) {
    return new BusinessException(400, "VALIDATION_FAILED", detail);
  }

  public static BusinessException conflict(String code, String detail) {
    return new BusinessException(409, code, detail);
  }
}
