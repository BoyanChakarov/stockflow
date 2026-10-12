package com.boyanchakarov.stockflow.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "app_users")
public class AppUser {
  @Id private UUID id;

  @Column(length = 80, nullable = false)
  private String username;

  @Column(length = 255, nullable = false)
  private String passwordHash;

  @Column(length = 16, nullable = false)
  private String role;

  @Column private boolean enabled;
  @Column private Instant createdAt;

  protected AppUser() {}

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getRole() {
    return role;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public AppUser(UUID id, String username, String passwordHash, String role, Instant now) {
    this.id = id;
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = true;
    this.createdAt = now;
  }
}
