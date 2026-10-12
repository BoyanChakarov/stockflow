package com.boyanchakarov.stockflow.inventory;

import com.boyanchakarov.stockflow.auth.ActorProvider;
import com.boyanchakarov.stockflow.catalog.*;
import com.boyanchakarov.stockflow.common.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
  private final ProductRepository products;
  private final StockMovementRepository movements;
  private final ActorProvider actors;
  private final Idempotency idempotency;
  private final Validation validation;
  private final Clock clock;

  public InventoryService(
      ProductRepository products,
      StockMovementRepository movements,
      ActorProvider actors,
      Idempotency idempotency,
      Validation validation,
      Clock clock) {
    this.products = products;
    this.movements = movements;
    this.actors = actors;
    this.idempotency = idempotency;
    this.validation = validation;
    this.clock = clock;
  }

  public StoredResponse receive(InventoryDtos.Receive request, UUID key) {
    var actor = actors.admin();
    validation.check(request);
    return idempotency.execute(
        actor.id(),
        "RECEIVE_STOCK",
        key,
        request,
        () -> {
          Product p =
              products
                  .lock(request.productId())
                  .orElseThrow(() -> BusinessException.missing("Product"));
          Instant now = clock.instant();
          p.receive(request.quantity(), now);
          var m =
              movements.save(
                  new StockMovement(
                      p,
                      null,
                      actor.id(),
                      "RECEIPT",
                      request.quantity(),
                      0,
                      request.reason(),
                      now));
          return new Idempotency.Result(InventoryDtos.Movement.of(m), 201, null);
        });
  }

  public StoredResponse adjust(InventoryDtos.Adjust request, UUID key) {
    var actor = actors.admin();
    validation.check(request);
    if (request.delta() == 0) throw BusinessException.invalid("delta must be nonzero.");
    return idempotency.execute(
        actor.id(),
        "ADJUST_STOCK",
        key,
        request,
        () -> {
          Product p =
              products
                  .lock(request.productId())
                  .orElseThrow(() -> BusinessException.missing("Product"));
          Instant now = clock.instant();
          p.adjust(request.delta(), now);
          var m =
              movements.save(
                  new StockMovement(
                      p,
                      null,
                      actor.id(),
                      "ADJUSTMENT",
                      request.delta(),
                      0,
                      request.reason(),
                      now));
          return new Idempotency.Result(InventoryDtos.Movement.of(m), 201, null);
        });
  }

  @Transactional(readOnly = true)
  public PageResponse<InventoryDtos.Movement> list(
      UUID productId, String type, Instant from, Instant to, int page, int size) {
    actors.admin();
    if (type != null
        && !Set.of("RECEIPT", "ADJUSTMENT", "RESERVE", "FULFIL", "RELEASE").contains(type))
      throw BusinessException.invalid("Unknown movement type.");
    return PageResponse.of(
        movements
            .findAll(
                Queries.<StockMovement>equal("productId", productId)
                    .and(Queries.equal("type", type))
                    .and(Queries.dateRange(from, to)),
                Queries.page(page, size, "createdAt,desc", Set.of("createdAt"), "createdAt,desc"))
            .map(InventoryDtos.Movement::of));
  }
}
