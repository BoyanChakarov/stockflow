package com.boyanchakarov.stockflow.catalog;

import com.boyanchakarov.stockflow.common.BusinessException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "products")
public class Product {
  @Id private UUID id;

  @Column(length = 64, nullable = false)
  private String sku;

  @Column(length = 200, nullable = false)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(precision = 12, scale = 2, nullable = false)
  private BigDecimal unitPrice;

  @Column private long reorderThreshold;
  @Column private boolean active;
  @Column private long onHand;
  @Column private long reserved;
  @Column private Instant createdAt;
  @Column private Instant updatedAt;

  protected Product() {}

  public UUID getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public long getReorderThreshold() {
    return reorderThreshold;
  }

  public boolean getActive() {
    return active;
  }

  public long getOnHand() {
    return onHand;
  }

  public long getReserved() {
    return reserved;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Product(
      UUID id,
      String sku,
      String name,
      String description,
      BigDecimal price,
      long threshold,
      Instant now) {
    this.id = id;
    this.sku = sku;
    this.name = name;
    this.description = description;
    this.unitPrice = price.setScale(2);
    this.reorderThreshold = threshold;
    this.active = true;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public long getAvailable() {
    return onHand - reserved;
  }

  public void edit(
      String name,
      String description,
      BigDecimal price,
      long threshold,
      boolean active,
      Instant now) {
    this.name = name;
    this.description = description;
    this.unitPrice = price.setScale(2);
    this.reorderThreshold = threshold;
    this.active = active;
    this.updatedAt = now;
  }

  public void receive(long quantity, Instant now) {
    if (quantity <= 0) throw BusinessException.invalid("Receipt quantity must be positive.");
    adjust(quantity, now);
  }

  public void adjust(long delta, Instant now) {
    if (delta == 0) throw BusinessException.invalid("Adjustment must be nonzero.");
    final long candidate;
    try {
      candidate = Math.addExact(onHand, delta);
    } catch (ArithmeticException e) {
      throw BusinessException.conflict("STOCK_LIMIT", "Stock counter limit exceeded.");
    }
    if (candidate < reserved)
      throw BusinessException.conflict(
          "INSUFFICIENT_STOCK",
          "A correction cannot consume reserved stock or make stock negative.");
    onHand = candidate;
    updatedAt = now;
  }

  public void reserve(int quantity, Instant now) {
    if (quantity <= 0 || quantity > getAvailable())
      throw BusinessException.conflict("INSUFFICIENT_STOCK", "Insufficient available stock.");
    reserved = Math.addExact(reserved, quantity);
    updatedAt = now;
  }

  public void fulfil(int quantity, Instant now) {
    checkReservation(quantity);
    onHand = Math.subtractExact(onHand, quantity);
    reserved -= quantity;
    updatedAt = now;
  }

  public void release(int quantity, Instant now) {
    checkReservation(quantity);
    reserved -= quantity;
    updatedAt = now;
  }

  private void checkReservation(int quantity) {
    if (quantity <= 0 || reserved < quantity || onHand < reserved)
      throw new IllegalStateException("Reservation invariant violated");
  }
}
