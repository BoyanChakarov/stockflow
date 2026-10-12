package com.boyanchakarov.stockflow.common;

import jakarta.validation.*;
import org.springframework.stereotype.Component;

@Component
public class Validation {
  private final Validator validator;

  public Validation(Validator validator) {
    this.validator = validator;
  }

  public <T> T check(T request) {
    var errors = validator.validate(request);
    if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    return request;
  }
}
