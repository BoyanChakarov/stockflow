package com.boyanchakarov.stockflow.inventory;

import com.boyanchakarov.stockflow.common.Text;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {
  public record Receive(
      @NotNull UUID productId,
      @NotNull @Min(1) @Max(1000000) Integer quantity,
      @NotBlank @Size(max = 500) String reason) {
    public Receive {
      reason = Text.trim(reason);
    }
  }

  public record Adjust(
      @NotNull UUID productId,
      @NotNull @Min(-1000000) @Max(1000000) Integer delta,
      @NotBlank @Size(max = 500) String reason) {
    public Adjust {
      reason = Text.trim(reason);
    }
  }

  public record Movement(
      UUID movementId,
      UUID productId,
      UUID orderId,
      UUID actorId,
      String type,
      long deltaOnHand,
      long deltaReserved,
      long onHandAfter,
      long reservedAfter,
      String reason,
      Instant createdAt) {
    public static Movement of(StockMovement m) {
      return new Movement(
          m.getId(),
          m.getProductId(),
          m.getOrderId(),
          m.getActorId(),
          m.getType(),
          m.getDeltaOnHand(),
          m.getDeltaReserved(),
          m.getOnHandAfter(),
          m.getReservedAfter(),
          m.getReason(),
          m.getCreatedAt());
    }
  }
}
