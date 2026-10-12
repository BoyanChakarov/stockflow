package com.boyanchakarov.stockflow.catalog;

import com.boyanchakarov.stockflow.auth.ActorProvider;
import com.boyanchakarov.stockflow.common.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class ProductService {
  private final ProductRepository products;
  private final ActorProvider actors;
  private final WriteTransactions tx;
  private final Clock clock;
  private final Validation validation;

  public ProductService(
      ProductRepository products,
      ActorProvider actors,
      WriteTransactions tx,
      Clock clock,
      Validation validation) {
    this.products = products;
    this.actors = actors;
    this.tx = tx;
    this.clock = clock;
    this.validation = validation;
  }

  @Transactional(readOnly = true)
  public PageResponse<ProductDtos.View> list(
      String q, Boolean active, int page, int size, String sort) {
    actors.current();
    return PageResponse.of(
        products
            .findAll(
                Queries.<Product>equal("active", active)
                    .and(Queries.searchFields(q, "sku", "name")),
                Queries.page(
                    page, size, sort, Set.of("sku", "name", "createdAt", "unitPrice"), "sku"))
            .map(ProductDtos.View::of));
  }

  @Transactional(readOnly = true)
  public ProductDtos.View get(UUID id) {
    actors.current();
    return ProductDtos.View.of(
        products.findById(id).orElseThrow(() -> BusinessException.missing("Product")));
  }

  public ProductDtos.View create(ProductDtos.Create request) {
    actors.admin();
    validation.check(request);
    return tx.run(
        () -> {
          if (products.existsBySku(request.sku()))
            throw BusinessException.conflict(
                "DUPLICATE_SKU", "A product with this SKU already exists.");
          return ProductDtos.View.of(
              products.saveAndFlush(
                  new Product(
                      UUID.randomUUID(),
                      request.sku(),
                      request.name(),
                      request.description(),
                      request.unitPrice(),
                      request.reorderThreshold(),
                      clock.instant())));
        });
  }

  public ProductDtos.View update(UUID id, JsonNode fields) {
    actors.admin();
    Patch patch =
        new Patch(fields, Set.of("name", "description", "unitPrice", "reorderThreshold", "active"));
    return tx.run(
        () -> {
          Product p = products.lock(id).orElseThrow(() -> BusinessException.missing("Product"));
          var v =
              validation.check(
                  new ProductDtos.Create(
                      p.getSku(),
                      patch.text("name", p.getName(), true),
                      patch.text("description", p.getDescription(), false),
                      patch.price("unitPrice", p.getUnitPrice()),
                      patch.number("reorderThreshold", p.getReorderThreshold())));
          p.edit(
              v.name(),
              v.description(),
              v.unitPrice(),
              v.reorderThreshold(),
              patch.bool("active", p.getActive()),
              clock.instant());
          return ProductDtos.View.of(p);
        });
  }
}
