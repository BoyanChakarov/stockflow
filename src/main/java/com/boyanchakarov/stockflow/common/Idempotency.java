package com.boyanchakarov.stockflow.common;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class Idempotency {
  private final WriteTransactions tx;
  private final JdbcTemplate jdbc;
  private final EntityManager em;
  private final JsonMapper json;
  private final Clock clock;
  private final ObjectProvider<BeforeCommitHook> hooks;

  public Idempotency(
      WriteTransactions tx,
      JdbcTemplate jdbc,
      EntityManager em,
      JsonMapper json,
      Clock clock,
      ObjectProvider<BeforeCommitHook> hooks) {
    this.tx = tx;
    this.jdbc = jdbc;
    this.em = em;
    this.json = json;
    this.clock = clock;
    this.hooks = hooks;
  }

  public record Result(Object body, int status, String location) {}

  public StoredResponse execute(
      UUID actor, String operation, UUID key, Object normalized, Supplier<Result> action) {
    if (key == null) throw BusinessException.invalid("Idempotency-Key UUID is required.");
    String hash = fingerprint(json.writeValueAsString(normalized));
    return tx.run(
        () -> {
          UUID id = UUID.randomUUID();
          List<UUID> inserted =
              jdbc.query(
                  "insert into"
                      + " idempotency_records(id,actor_id,operation,idempotency_key,request_hash)"
                      + " values (?,?,?,?,?) on conflict (actor_id,operation,idempotency_key) do"
                      + " nothing returning id",
                  (r, n) -> r.getObject(1, UUID.class),
                  id,
                  actor,
                  operation,
                  key,
                  hash);
          if (inserted.isEmpty()) {
            return jdbc.queryForObject(
                "select"
                    + " request_hash,response_status,response_body::text,response_location,completed_at"
                    + " from idempotency_records where actor_id=? and operation=? and"
                    + " idempotency_key=?",
                (r, n) -> {
                  if (!hash.equals(r.getString(1)))
                    throw BusinessException.conflict(
                        "IDEMPOTENCY_KEY_REUSED",
                        "This key was already used with a different request.");
                  if (r.getObject(2) == null || r.getString(3) == null || r.getObject(5) == null)
                    throw new IllegalStateException("Incomplete committed idempotency record");
                  return new StoredResponse(r.getInt(2), r.getString(3), r.getString(4));
                },
                actor,
                operation,
                key);
          }
          Result result = Objects.requireNonNull(action.get());
          em.flush();
          hooks.orderedStream().forEach(h -> h.afterWrites(operation));
          String body = json.writeValueAsString(result.body());
          if (result.status() < 200 || result.status() > 299)
            throw new IllegalStateException("Only successful responses can be stored");
          int updated =
              jdbc.update(
                  "update idempotency_records set response_status=?,response_body=cast(? as"
                      + " jsonb),response_location=?,completed_at=? where id=? and completed_at is"
                      + " null",
                  result.status(),
                  body,
                  result.location(),
                  java.sql.Timestamp.from(clock.instant()),
                  id);
          if (updated != 1) throw new IllegalStateException("Idempotency finalization failed");
          return new StoredResponse(result.status(), body, result.location());
        });
  }

  static String fingerprint(String canonical) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
