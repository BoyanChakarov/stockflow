package com.boyanchakarov.stockflow.common;

import java.net.URI;
import org.springframework.http.*;

public record StoredResponse(int status, String body, String location) {
  public ResponseEntity<String> http() {
    var b = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
    if (location != null) b.location(URI.create(location));
    return b.body(body);
  }
}
