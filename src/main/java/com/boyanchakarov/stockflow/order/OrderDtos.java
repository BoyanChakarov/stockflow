package com.boyanchakarov.stockflow.order;

import com.boyanchakarov.stockflow.common.Text;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class OrderDtos {
  public record Item(@NotNull UUID productId, @NotNull @Min(1) @Max(1000000) Integer quantity) {}

  public record Create(
      @NotNull UUID customerId,
      @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Item> items,
      @Size(max = 2000) String notes) {
    public Create {
      notes = Text.optional(notes);
      items = items == null ? null : List.copyOf(items);
    }
  }

  public record Cancel(@NotBlank @Size(max = 500) String reason) {
    public Cancel {
      reason = Text.trim(reason);
    }
  }

  public record CustomerSnapshot(UUID id, String name, String email, String phone) {}

  public record Line(
      UUID productId, String sku, String name, int quantity, String unitPrice, String lineTotal) {}

  public record Detail(
      UUID id,
      OrderStatus status,
      CustomerSnapshot customer,
      List<Line> items,
      String currency,
      String total,
      String notes,
      UUID createdBy,
      Instant createdAt,
      Instant fulfilledAt,
      Instant cancelledAt,
      String cancellationReason) {
    public static Detail of(Order o) {
      return new Detail(
          o.getId(),
          o.getStatus(),
          new CustomerSnapshot(
              o.getCustomerId(),
              o.getCustomerNameSnapshot(),
              o.getCustomerEmailSnapshot(),
              o.getCustomerPhoneSnapshot()),
          o.getItems().stream()
              .map(
                  i ->
                      new Line(
                          i.getProductId(),
                          i.getSkuSnapshot(),
                          i.getNameSnapshot(),
                          i.getQuantity(),
                          i.getUnitPriceSnapshot().toPlainString(),
                          i.getLineTotal().toPlainString()))
              .toList(),
          o.getCurrency(),
          o.getTotal().toPlainString(),
          o.getNotes(),
          o.getCreatedBy(),
          o.getCreatedAt(),
          o.getFulfilledAt(),
          o.getCancelledAt(),
          o.getCancellationReason());
    }
  }

  public record Summary(
      UUID id,
      String status,
      UUID customerId,
      String customerName,
      long quantity,
      String total,
      String currency,
      UUID createdBy,
      Instant createdAt,
      Instant fulfilledAt,
      Instant cancelledAt) {}

  public record Event(
      UUID id,
      UUID orderId,
      UUID actorId,
      String fromStatus,
      String toStatus,
      String reason,
      Instant createdAt) {
    public static Event of(OrderEvent e) {
      return new Event(
          e.getId(),
          e.getOrderId(),
          e.getActorId(),
          e.getFromStatus(),
          e.getToStatus(),
          e.getReason(),
          e.getCreatedAt());
    }
  }
}
