package com.boyanchakarov.stockflow.common;

/** Optional internal observer used by integration tests to prove rollback after flushed writes. */
@FunctionalInterface
public interface BeforeCommitHook {
  void afterWrites(String operation);
}
