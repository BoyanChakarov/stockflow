package com.boyanchakarov.stockflow.inventory;

import com.boyanchakarov.stockflow.common.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
  private final InventoryService service;

  public InventoryController(InventoryService service) {
    this.service = service;
  }

  @PostMapping("/receipts")
  public ResponseEntity<String> receive(
      @Valid @RequestBody InventoryDtos.Receive request,
      @RequestHeader("Idempotency-Key") UUID key) {
    return service.receive(request, key).http();
  }

  @PostMapping("/adjustments")
  public ResponseEntity<String> adjust(
      @Valid @RequestBody InventoryDtos.Adjust request,
      @RequestHeader("Idempotency-Key") UUID key) {
    return service.adjust(request, key).http();
  }

  @GetMapping("/movements")
  public PageResponse<InventoryDtos.Movement> list(
      @RequestParam(required = false) UUID productId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(productId, type, from, to, page, size);
  }
}
