package com.boyanchakarov.stockflow.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Reuses the API error policy while giving browser navigation a readable error page. */
@Order(-1)
@ControllerAdvice(assignableTypes = PageController.class)
public class PageErrors {
  private final ApiErrors errors;

  public PageErrors(ApiErrors errors) {
    this.errors = errors;
  }

  @ExceptionHandler(Exception.class)
  public ModelAndView handle(Exception error, HttpServletRequest request) {
    var response = errors.handle(error, request);
    if (response.getStatusCode().value() == 401) return new ModelAndView("redirect:/login");
    var view = new ModelAndView("error");
    view.setStatus(response.getStatusCode());
    view.addObject("problem", response.getBody());
    return view;
  }
}
