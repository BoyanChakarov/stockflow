package com.boyanchakarov.stockflow.order;

import com.boyanchakarov.stockflow.common.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
  private final OrderService service;
  private final OrderQueries queries;

  public OrderController(OrderService service, OrderQueries queries) {
    this.service = service;
    this.queries = queries;
  }

  @GetMapping
  public PageResponse<OrderDtos.Summary> list(
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return queries.list(status, customerId, from, to, page, size, sort);
  }

  @GetMapping("/{id}")
  public OrderDtos.Detail get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping("/{id}/events")
  public List<OrderDtos.Event> events(@PathVariable UUID id) {
    return service.events(id);
  }

  @PostMapping
  public ResponseEntity<String> create(
      @Valid @RequestBody OrderDtos.Create request, @RequestHeader("Idempotency-Key") UUID key) {
    return service.create(request, key).http();
  }

  @PostMapping("/{id}/fulfil")
  public OrderDtos.Detail fulfil(
      @PathVariable UUID id, @RequestBody(required = false) EmptyCommand command) {
    return service.fulfil(id);
  }

  public record EmptyCommand() {}

  @PostMapping("/{id}/cancel")
  public OrderDtos.Detail cancel(
      @PathVariable UUID id, @Valid @RequestBody OrderDtos.Cancel request) {
    return service.cancel(id, request);
  }
}
