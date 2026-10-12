package com.boyanchakarov.stockflow.order;

import com.boyanchakarov.stockflow.catalog.Product;
import com.boyanchakarov.stockflow.customer.Customer;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "orders")
public class Order implements org.springframework.data.domain.Persistable<UUID> {
  @Transient private boolean newEntity = true;

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostPersist
  @PostLoad
  void markPersisted() {
    newEntity = false;
  }

  @Id private UUID id;
  @Column private UUID customerId;

  @Column(length = 200)
  private String customerNameSnapshot;

  @Column(length = 254)
  private String customerEmailSnapshot;

  @Column(length = 40)
  private String customerPhoneSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private OrderStatus status;

  @Column(columnDefinition = "char(3)")
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  private String currency;

  @Column(precision = 18, scale = 2)
  private BigDecimal total;

  @Column(length = 2000)
  private String notes;

  @Column private UUID createdBy;
  @Column private Instant createdAt;
  @Column private Instant fulfilledAt;
  @Column private Instant cancelledAt;

  @Column(length = 500)
  private String cancellationReason;

  @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
  @OrderBy("skuSnapshot ASC, id ASC")
  private List<OrderItem> items;

  protected Order() {}

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getCustomerNameSnapshot() {
    return customerNameSnapshot;
  }

  public String getCustomerEmailSnapshot() {
    return customerEmailSnapshot;
  }

  public String getCustomerPhoneSnapshot() {
    return customerPhoneSnapshot;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getFulfilledAt() {
    return fulfilledAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public List<OrderItem> getItems() {
    return items;
  }

  public Order(UUID id, Customer customer, String notes, UUID actor, Instant now) {
    this.id = id;
    this.customerId = customer.getId();
    this.customerNameSnapshot = customer.getName();
    this.customerEmailSnapshot = customer.getEmail();
    this.customerPhoneSnapshot = customer.getPhone();
    this.status = OrderStatus.CONFIRMED;
    this.currency = "EUR";
    this.total = new BigDecimal("0.00");
    this.notes = notes;
    this.createdBy = actor;
    this.createdAt = now;
    this.items = new ArrayList<>();
  }

  public void addItem(Product product, int quantity) {
    OrderItem item = new OrderItem(this, product, quantity);
    items.add(item);
    total = total.add(item.getLineTotal());
  }

  public void fulfil(Instant now) {
    status = OrderStatus.FULFILLED;
    fulfilledAt = now;
  }

  public void cancel(String reason, Instant now) {
    status = OrderStatus.CANCELLED;
    cancelledAt = now;
    cancellationReason = reason;
  }
}
