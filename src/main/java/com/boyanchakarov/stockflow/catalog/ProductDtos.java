package com.boyanchakarov.stockflow.catalog;

import com.boyanchakarov.stockflow.common.Text;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ProductDtos {
  public record Create(
      @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String sku,
      @NotBlank @Size(max = 200) String name,
      @Size(max = 2000) String description,
      @NotNull @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2)
          BigDecimal unitPrice,
      @NotNull @Min(0) Long reorderThreshold) {
    public Create {
      sku = Text.sku(sku);
      name = Text.trim(name);
      description = Text.optional(description);
    }
  }

  public record View(
      UUID id,
      String sku,
      String name,
      String description,
      String unitPrice,
      long reorderThreshold,
      boolean active,
      long onHand,
      long reserved,
      long available,
      Instant createdAt,
      Instant updatedAt) {
    public static View of(Product p) {
      return new View(
          p.getId(),
          p.getSku(),
          p.getName(),
          p.getDescription(),
          p.getUnitPrice().toPlainString(),
          p.getReorderThreshold(),
          p.getActive(),
          p.getOnHand(),
          p.getReserved(),
          p.getAvailable(),
          p.getCreatedAt(),
          p.getUpdatedAt());
    }
  }
}
