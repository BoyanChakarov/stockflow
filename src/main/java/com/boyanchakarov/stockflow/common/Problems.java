package com.boyanchakarov.stockflow.common;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class Problems {
  private final JsonMapper json;

  public Problems(JsonMapper json) {
    this.json = json;
  }

  public Map<String, Object> body(
      HttpServletRequest request, int status, String code, String detail) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("type", "about:blank");
    b.put("title", HttpStatus.valueOf(status).getReasonPhrase());
    b.put("status", status);
    b.put("code", code);
    b.put("detail", detail);
    b.put("instance", request.getRequestURI());
    b.put("requestId", request.getAttribute("requestId"));
    return b;
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.getWriter().write(json.writeValueAsString(body(request, status, code, detail)));
  }
}
