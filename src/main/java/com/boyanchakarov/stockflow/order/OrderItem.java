package com.boyanchakarov.stockflow.order;

import com.boyanchakarov.stockflow.catalog.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "order_items")
public class OrderItem {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id")
  private Order order;

  @Column private UUID productId;

  @Column(length = 64)
  private String skuSnapshot;

  @Column(length = 200)
  private String nameSnapshot;

  @Column(precision = 12, scale = 2)
  private BigDecimal unitPriceSnapshot;

  @Column private int quantity;

  @Column(precision = 18, scale = 2)
  private BigDecimal lineTotal;

  protected OrderItem() {}

  public UUID getId() {
    return id;
  }

  public Order getOrder() {
    return order;
  }

  public UUID getProductId() {
    return productId;
  }

  public String getSkuSnapshot() {
    return skuSnapshot;
  }

  public String getNameSnapshot() {
    return nameSnapshot;
  }

  public BigDecimal getUnitPriceSnapshot() {
    return unitPriceSnapshot;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }

  public OrderItem(Order order, Product product, int quantity) {
    this.id = UUID.randomUUID();
    this.order = order;
    this.productId = product.getId();
    this.skuSnapshot = product.getSku();
    this.nameSnapshot = product.getName();
    this.unitPriceSnapshot = product.getUnitPrice();
    this.quantity = quantity;
    this.lineTotal = unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity)).setScale(2);
  }
}
