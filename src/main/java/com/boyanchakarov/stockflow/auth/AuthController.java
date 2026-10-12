package com.boyanchakarov.stockflow.auth;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final ActorProvider actors;

  public AuthController(ActorProvider actors) {
    this.actors = actors;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of(
        "headerName",
        token.getHeaderName(),
        "parameterName",
        token.getParameterName(),
        "token",
        token.getToken());
  }

  @GetMapping("/me")
  public ActorProvider.Actor me() {
    return actors.current();
  }
}
