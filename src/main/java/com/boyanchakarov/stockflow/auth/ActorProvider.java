package com.boyanchakarov.stockflow.auth;

import com.boyanchakarov.stockflow.common.BusinessException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ActorProvider {
  private final AppUserRepository users;

  public ActorProvider(AppUserRepository users) {
    this.users = users;
  }

  public record Actor(UUID id, String username, String role) {
    public boolean admin() {
      return "ADMIN".equals(role);
    }
  }

  public Actor current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
      throw new BusinessException(401, "UNAUTHENTICATED", "Sign in to continue.");
    AppUser u =
        users
            .findByUsername(auth.getName())
            .filter(AppUser::getEnabled)
            .orElseThrow(
                () -> new BusinessException(401, "UNAUTHENTICATED", "Sign in to continue."));
    return new Actor(u.getId(), u.getUsername(), u.getRole());
  }

  public Actor admin() {
    Actor a = current();
    if (!a.admin())
      throw new BusinessException(403, "FORBIDDEN", "Administrator permission is required.");
    return a;
  }
}
