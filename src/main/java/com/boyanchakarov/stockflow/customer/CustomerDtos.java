package com.boyanchakarov.stockflow.customer;

import com.boyanchakarov.stockflow.common.Text;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class CustomerDtos {
  public record Create(
      @NotBlank @Size(max = 200) String name,
      @Email @Size(max = 254) String email,
      @Size(max = 40) String phone,
      @Size(max = 2000) String notes) {
    public Create {
      name = Text.trim(name);
      email = Text.optional(email);
      phone = Text.optional(phone);
      notes = Text.optional(notes);
    }
  }

  public record View(
      UUID id,
      String name,
      String email,
      String phone,
      String notes,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    public static View of(Customer c) {
      return new View(
          c.getId(),
          c.getName(),
          c.getEmail(),
          c.getPhone(),
          c.getNotes(),
          c.getActive(),
          c.getCreatedAt(),
          c.getUpdatedAt());
    }
  }
}
