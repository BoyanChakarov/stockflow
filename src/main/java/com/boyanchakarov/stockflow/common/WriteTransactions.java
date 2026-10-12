package com.boyanchakarov.stockflow.common;

import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class WriteTransactions {
  private final TransactionTemplate tx;
  private final JdbcTemplate jdbc;

  public WriteTransactions(PlatformTransactionManager manager, JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    tx = new TransactionTemplate(manager);
    tx.setTimeout(15);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public <T> T run(Supplier<T> action) {
    return tx.execute(
        s -> {
          jdbc.queryForObject("select set_config('lock_timeout', ?, true)", String.class, "5s");
          return action.get();
        });
  }
}
