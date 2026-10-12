package com.boyanchakarov.stockflow.customer;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "customers")
public class Customer {
  @Id private UUID id;

  @Column(length = 200, nullable = false)
  private String name;

  @Column(length = 254)
  private String email;

  @Column(length = 40)
  private String phone;

  @Column(length = 2000)
  private String notes;

  @Column private boolean active;
  @Column private Instant createdAt;
  @Column private Instant updatedAt;

  protected Customer() {}

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getNotes() {
    return notes;
  }

  public boolean getActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Customer(UUID id, String name, String email, String phone, String notes, Instant now) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.phone = phone;
    this.notes = notes;
    this.active = true;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void edit(
      String name, String email, String phone, String notes, boolean active, Instant now) {
    this.name = name;
    this.email = email;
    this.phone = phone;
    this.notes = notes;
    this.active = active;
    this.updatedAt = now;
  }
}
