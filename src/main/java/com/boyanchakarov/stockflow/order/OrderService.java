package com.boyanchakarov.stockflow.order;

import com.boyanchakarov.stockflow.auth.ActorProvider;
import com.boyanchakarov.stockflow.catalog.*;
import com.boyanchakarov.stockflow.common.*;
import com.boyanchakarov.stockflow.customer.*;
import com.boyanchakarov.stockflow.inventory.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final OrderRepository orders;
  private final ProductRepository products;
  private final CustomerRepository customers;
  private final StockMovementRepository movements;
  private final OrderEventRepository events;
  private final ActorProvider actors;
  private final Idempotency idempotency;
  private final WriteTransactions tx;
  private final Validation validation;
  private final Clock clock;

  public OrderService(
      OrderRepository orders,
      ProductRepository products,
      CustomerRepository customers,
      StockMovementRepository movements,
      OrderEventRepository events,
      ActorProvider actors,
      Idempotency idempotency,
      WriteTransactions tx,
      Validation validation,
      Clock clock) {
    this.orders = orders;
    this.products = products;
    this.customers = customers;
    this.movements = movements;
    this.events = events;
    this.actors = actors;
    this.idempotency = idempotency;
    this.tx = tx;
    this.validation = validation;
    this.clock = clock;
  }

  public StoredResponse create(OrderDtos.Create request, UUID key) {
    var actor = actors.current();
    validation.check(request);
    if (request.items().stream().map(OrderDtos.Item::productId).distinct().count()
        != request.items().size())
      throw BusinessException.invalid("Each product may appear only once.");
    var normalized =
        new OrderDtos.Create(
            request.customerId(),
            request.items().stream()
                .sorted(Comparator.comparing(i -> i.productId().toString()))
                .toList(),
            request.notes());
    return idempotency.execute(
        actor.id(),
        "CREATE_ORDER",
        key,
        normalized,
        () -> {
          Customer customer =
              customers
                  .lock(normalized.customerId())
                  .orElseThrow(() -> BusinessException.missing("Customer"));
          if (!customer.getActive())
            throw BusinessException.conflict(
                "CUSTOMER_INACTIVE", "An archived customer cannot be used for a new order.");
          Map<UUID, Product> locked =
              lockProducts(normalized.items().stream().map(OrderDtos.Item::productId).toList());
          List<Map<String, Object>> shortages = new ArrayList<>();
          for (var item : normalized.items()) {
            Product p = locked.get(item.productId());
            if (!p.getActive())
              throw BusinessException.conflict(
                  "PRODUCT_INACTIVE", "An archived product cannot be used for a new order.");
            if (p.getAvailable() < item.quantity())
              shortages.add(
                  Map.of(
                      "productId",
                      p.getId(),
                      "requested",
                      item.quantity(),
                      "available",
                      p.getAvailable()));
          }
          if (!shortages.isEmpty())
            throw new BusinessException(
                409,
                "INSUFFICIENT_STOCK",
                "The requested quantities exceed available stock.",
                Map.of("shortages", shortages));
          Instant now = clock.instant();
          Order order = new Order(UUID.randomUUID(), customer, normalized.notes(), actor.id(), now);
          for (var item : normalized.items())
            order.addItem(locked.get(item.productId()), item.quantity());
          orders.save(order);
          for (var item : normalized.items()) {
            Product p = locked.get(item.productId());
            p.reserve(item.quantity(), now);
            movements.save(
                new StockMovement(
                    p,
                    order.getId(),
                    actor.id(),
                    "RESERVE",
                    0,
                    item.quantity(),
                    "Order confirmed",
                    now));
          }
          events.save(new OrderEvent(order.getId(), actor.id(), null, "CONFIRMED", null, now));
          return new Idempotency.Result(
              OrderDtos.Detail.of(order), 201, "/api/v1/orders/" + order.getId());
        });
  }

  public OrderDtos.Detail fulfil(UUID id) {
    return transition(id, null, false);
  }

  public OrderDtos.Detail cancel(UUID id, OrderDtos.Cancel request) {
    validation.check(request);
    return transition(id, request.reason(), true);
  }

  private OrderDtos.Detail transition(UUID id, String reason, boolean cancel) {
    var actor = actors.current();
    return tx.run(
        () -> {
          Order order = orders.lock(id).orElseThrow(() -> BusinessException.missing("Order"));
          OrderStatus target = cancel ? OrderStatus.CANCELLED : OrderStatus.FULFILLED;
          if (order.getStatus() == target) return OrderDtos.Detail.of(order);
          if (order.getStatus() != OrderStatus.CONFIRMED)
            throw BusinessException.conflict(
                "INVALID_ORDER_TRANSITION",
                "An order in " + order.getStatus() + " cannot be changed to " + target + ".");
          Map<UUID, Product> locked =
              lockProducts(order.getItems().stream().map(OrderItem::getProductId).toList());
          Instant now = clock.instant();
          for (OrderItem item : order.getItems()) {
            Product p = locked.get(item.getProductId());
            if (cancel) p.release(item.getQuantity(), now);
            else p.fulfil(item.getQuantity(), now);
            movements.save(
                new StockMovement(
                    p,
                    id,
                    actor.id(),
                    cancel ? "RELEASE" : "FULFIL",
                    cancel ? 0 : -item.getQuantity(),
                    -item.getQuantity(),
                    cancel ? reason : "Order fulfilled",
                    now));
          }
          if (cancel) order.cancel(reason, now);
          else order.fulfil(now);
          events.save(new OrderEvent(id, actor.id(), "CONFIRMED", target.name(), reason, now));
          return OrderDtos.Detail.of(order);
        });
  }

  private Map<UUID, Product> lockProducts(List<UUID> ids) {
    Map<UUID, Product> result = new LinkedHashMap<>();
    ids.stream()
        .distinct()
        .sorted(Comparator.comparing(UUID::toString))
        .forEach(
            id ->
                result.put(
                    id, products.lock(id).orElseThrow(() -> BusinessException.missing("Product"))));
    return result;
  }

  @Transactional(readOnly = true)
  public OrderDtos.Detail get(UUID id) {
    actors.current();
    return OrderDtos.Detail.of(
        orders.findById(id).orElseThrow(() -> BusinessException.missing("Order")));
  }

  @Transactional(readOnly = true)
  public List<OrderDtos.Event> events(UUID id) {
    actors.current();
    if (!orders.existsById(id)) throw BusinessException.missing("Order");
    return events.findByOrderIdOrderByCreatedAtAscIdAsc(id).stream()
        .map(OrderDtos.Event::of)
        .toList();
  }
}
