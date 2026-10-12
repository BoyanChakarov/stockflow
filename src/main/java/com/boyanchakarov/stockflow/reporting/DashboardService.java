package com.boyanchakarov.stockflow.reporting;

import com.boyanchakarov.stockflow.auth.ActorProvider;
import com.boyanchakarov.stockflow.catalog.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DashboardService {
  private final JdbcTemplate jdbc;
  private final ProductRepository products;
  private final ActorProvider actors;

  public DashboardService(JdbcTemplate jdbc, ProductRepository products, ActorProvider actors) {
    this.jdbc = jdbc;
    this.products = products;
    this.actors = actors;
  }

  public record Dashboard(
      long activeProductCount,
      long confirmedOrderCount,
      long lowStockProductCount,
      List<ProductDtos.View> lowStock) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Dashboard get() {
    actors.current();
    var low =
        products.findAll(
            (r, q, b) -> {
              q.orderBy(
                  b.asc(b.diff(r.<Long>get("onHand"), r.<Long>get("reserved"))),
                  b.asc(r.get("sku")),
                  b.asc(r.get("id")));
              return b.and(
                  b.isTrue(r.get("active")),
                  b.le(
                      b.diff(r.<Long>get("onHand"), r.<Long>get("reserved")),
                      r.<Long>get("reorderThreshold")));
            },
            PageRequest.of(0, 10));
    return new Dashboard(
        jdbc.queryForObject("select count(*) from products where active", Long.class),
        jdbc.queryForObject("select count(*) from orders where status='CONFIRMED'", Long.class),
        low.getTotalElements(),
        low.stream().map(ProductDtos.View::of).toList());
  }
}
