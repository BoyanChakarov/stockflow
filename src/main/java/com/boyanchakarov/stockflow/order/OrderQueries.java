package com.boyanchakarov.stockflow.order;

import com.boyanchakarov.stockflow.auth.ActorProvider;
import com.boyanchakarov.stockflow.common.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueries {
  private final NamedParameterJdbcTemplate jdbc;
  private final ActorProvider actors;

  public OrderQueries(NamedParameterJdbcTemplate jdbc, ActorProvider actors) {
    this.jdbc = jdbc;
    this.actors = actors;
  }

  @Transactional(readOnly = true)
  public PageResponse<OrderDtos.Summary> list(
      OrderStatus status,
      UUID customerId,
      Instant from,
      Instant to,
      int page,
      int size,
      String sort) {
    actors.current();
    Queries.dates(from, to);
    var paging =
        Queries.page(page, size, sort, Set.of("createdAt", "total", "status"), "createdAt,desc");
    Map<String, Object> params = new HashMap<>();
    StringBuilder where = new StringBuilder(" where 1=1");
    if (status != null) {
      where.append(" and o.status=:status");
      params.put("status", status.name());
    }
    if (customerId != null) {
      where.append(" and o.customer_id=:customer");
      params.put("customer", customerId);
    }
    if (from != null) {
      where.append(" and o.created_at>=:from");
      params.put("from", Timestamp.from(from));
    }
    if (to != null) {
      where.append(" and o.created_at<:to");
      params.put("to", Timestamp.from(to));
    }
    long count =
        Objects.requireNonNull(
            jdbc.queryForObject("select count(*) from orders o" + where, params, Long.class));
    var ordering = paging.getSort().iterator().next();
    String column =
        Map.of("createdAt", "created_at", "total", "total", "status", "status")
            .get(ordering.getProperty());
    params.put("limit", size);
    params.put("offset", paging.getOffset());
    // A scalar aggregate is performed by PostgreSQL, with no entity traversal or per-row network
    // query.
    var content =
        jdbc.query(
            "select o.*, (select sum(i.quantity) from order_items i where i.order_id=o.id) as"
                + " quantity from orders o"
                + where
                + " order by o."
                + column
                + " "
                + ordering.getDirection().name()
                + ",o.id asc limit :limit offset :offset",
            params,
            (r, n) ->
                new OrderDtos.Summary(
                    r.getObject("id", UUID.class),
                    r.getString("status"),
                    r.getObject("customer_id", UUID.class),
                    r.getString("customer_name_snapshot"),
                    r.getLong("quantity"),
                    r.getBigDecimal("total").toPlainString(),
                    r.getString("currency"),
                    r.getObject("created_by", UUID.class),
                    instant(r, "created_at"),
                    instant(r, "fulfilled_at"),
                    instant(r, "cancelled_at")));
    return PageResponse.of(content, page, size, count);
  }

  private static Instant instant(ResultSet r, String column) throws SQLException {
    Timestamp t = r.getTimestamp(column);
    return t == null ? null : t.toInstant();
  }
}
