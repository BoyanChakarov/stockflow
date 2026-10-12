package com.boyanchakarov.stockflow.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "order_events")
public class OrderEvent {
  @Id private UUID id;
  @Column private UUID orderId;
  @Column private UUID actorId;

  @Column(length = 16)
  private String fromStatus;

  @Column(length = 16)
  private String toStatus;

  @Column(length = 500)
  private String reason;

  @Column private Instant createdAt;

  protected OrderEvent() {}

  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getFromStatus() {
    return fromStatus;
  }

  public String getToStatus() {
    return toStatus;
  }

  public String getReason() {
    return reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public OrderEvent(UUID orderId, UUID actor, String from, String to, String reason, Instant now) {
    this.id = UUID.randomUUID();
    this.orderId = orderId;
    this.actorId = actor;
    this.fromStatus = from;
    this.toStatus = to;
    this.reason = reason;
    this.createdAt = now;
  }
}
