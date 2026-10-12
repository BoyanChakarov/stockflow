package com.boyanchakarov.stockflow.catalog;

import com.boyanchakarov.stockflow.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
  private final ProductService service;

  public ProductController(ProductService service) {
    this.service = service;
  }

  @GetMapping
  public PageResponse<ProductDtos.View> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return service.list(q, active, page, size, sort);
  }

  @GetMapping("/{id}")
  public ProductDtos.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  public ResponseEntity<ProductDtos.View> create(@Valid @RequestBody ProductDtos.Create request) {
    var result = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/products/" + result.id())).body(result);
  }

  @PatchMapping("/{id}")
  public ProductDtos.View update(@PathVariable UUID id, @RequestBody JsonNode patch) {
    return service.update(id, patch);
  }
}
