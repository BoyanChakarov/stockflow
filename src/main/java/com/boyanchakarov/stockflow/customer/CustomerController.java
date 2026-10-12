package com.boyanchakarov.stockflow.customer;

import com.boyanchakarov.stockflow.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping
  public PageResponse<CustomerDtos.View> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return service.list(q, active, page, size, sort);
  }

  @GetMapping("/{id}")
  public CustomerDtos.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<CustomerDtos.View> create(@Valid @RequestBody CustomerDtos.Create request) {
    var result = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/customers/" + result.id())).body(result);
  }

  @PatchMapping("/{id}")
  public CustomerDtos.View update(@PathVariable UUID id, @RequestBody JsonNode patch) {
    return service.update(id, patch);
  }
}
