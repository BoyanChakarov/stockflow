package com.boyanchakarov.stockflow.inventory;

import com.boyanchakarov.stockflow.catalog.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
  @Id private UUID id;
  @Column private UUID productId;
  @Column private UUID orderId;
  @Column private UUID actorId;

  @Column(length = 16)
  private String type;

  @Column private long deltaOnHand;
  @Column private long deltaReserved;
  @Column private long onHandAfter;
  @Column private long reservedAfter;

  @Column(length = 500)
  private String reason;

  @Column private Instant createdAt;

  protected StockMovement() {}

  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return productId;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getType() {
    return type;
  }

  public long getDeltaOnHand() {
    return deltaOnHand;
  }

  public long getDeltaReserved() {
    return deltaReserved;
  }

  public long getOnHandAfter() {
    return onHandAfter;
  }

  public long getReservedAfter() {
    return reservedAfter;
  }

  public String getReason() {
    return reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public StockMovement(
      Product product,
      UUID orderId,
      UUID actor,
      String type,
      long onHandDelta,
      long reservedDelta,
      String reason,
      Instant now) {
    this.id = UUID.randomUUID();
    this.productId = product.getId();
    this.orderId = orderId;
    this.actorId = actor;
    this.type = type;
    this.deltaOnHand = onHandDelta;
    this.deltaReserved = reservedDelta;
    this.onHandAfter = product.getOnHand();
    this.reservedAfter = product.getReserved();
    this.reason = reason;
    this.createdAt = now;
  }
}
