package com.boyanchakarov.stockflow.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
  private final Problems problems;

  public ApiErrors(Problems problems) {
    this.problems = problems;
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handle(Exception error, HttpServletRequest request) {
    int status = 500;
    String code = "INTERNAL_ERROR",
        detail =
            "The request could not be completed. Use the request ID when reporting this problem.";
    Map<String, Object> extra = new LinkedHashMap<>();
    if (error instanceof BusinessException b) {
      status = b.status();
      code = b.code();
      detail = b.getMessage();
      extra.putAll(b.properties());
    } else if (error instanceof MethodArgumentNotValidException v) {
      status = 400;
      code = "VALIDATION_FAILED";
      detail = "Correct the highlighted fields.";
      extra.put(
          "fieldErrors",
          v.getBindingResult().getFieldErrors().stream()
              .map(
                  f ->
                      Map.of(
                          "field",
                          f.getField(),
                          "message",
                          Objects.requireNonNullElse(f.getDefaultMessage(), "Invalid value")))
              .toList());
    } else if (error instanceof ConstraintViolationException v) {
      status = 400;
      code = "VALIDATION_FAILED";
      detail = "Correct the highlighted fields.";
      extra.put(
          "fieldErrors",
          v.getConstraintViolations().stream()
              .map(f -> Map.of("field", f.getPropertyPath().toString(), "message", f.getMessage()))
              .toList());
    } else if (error instanceof HttpMessageNotReadableException
        || error instanceof MethodArgumentTypeMismatchException
        || error instanceof MissingRequestHeaderException
        || error instanceof MissingServletRequestParameterException) {
      status = 400;
      code = "INVALID_REQUEST";
      detail = "Malformed request, unknown property, or missing required parameter/header.";
    } else if (error instanceof AccessDeniedException) {
      status = 403;
      code = "FORBIDDEN";
      detail = "You do not have permission for this action.";
    } else if (error instanceof DataIntegrityViolationException) {
      status = 409;
      code = "DATA_CONFLICT";
      detail = "The request conflicts with an existing record or a data constraint.";
    } else if (error instanceof org.springframework.web.ErrorResponse response) {
      status = response.getStatusCode().value();
      code = status == 404 ? "NOT_FOUND" : "INVALID_REQUEST";
      detail = status == 404 ? "The requested resource was not found." : "The HTTP method or request format is not supported.";
    }
    for (Throwable e = error; e != null; e = e.getCause())
      if (e instanceof java.sql.SQLException sql) {
        if (Set.of("55P03", "40P01", "40001", "57014")
            .contains(Objects.requireNonNullElse(sql.getSQLState(), ""))) {
          status = 503;
          code = "RETRYABLE_CONFLICT";
          detail = "Another operation is in progress. Retry with the same idempotency key.";
          break;
        }
        if ("23505".equals(sql.getSQLState())
            && sql.getMessage() != null
            && sql.getMessage().contains("products_sku_key")) {
          status = 409;
          code = "DUPLICATE_SKU";
          detail = "A product with this SKU already exists.";
        }
      }
    if (status == 500)
      LoggerFactory.getLogger(ApiErrors.class).error("Unhandled request failure", error);
    Map<String, Object> body = problems.body(request, status, code, detail);
    body.putAll(extra);
    if (status == 400) body.putIfAbsent("fieldErrors", List.of());
    var builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (status == 503) builder.header("Retry-After", "1");
    return builder.body(body);
  }
}
