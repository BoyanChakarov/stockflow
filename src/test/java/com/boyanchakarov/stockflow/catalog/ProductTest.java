package com.boyanchakarov.stockflow.catalog;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductTest {
  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

  private Product product() {
    return new Product(UUID.randomUUID(), "MUG", "Mug", null, new BigDecimal("12.50"), 5, now);
  }

  @Test
  void reservationFulfilmentAndReleaseMaintainCounters() {
    var p = product();
    p.receive(10, now);
    p.reserve(3, now);
    assertThat(p.getAvailable()).isEqualTo(7);
    p.fulfil(3, now);
    assertThat(p.getOnHand()).isEqualTo(7);
    assertThat(p.getReserved()).isZero();
    p.reserve(2, now);
    p.release(2, now);
    assertThat(p.getAvailable()).isEqualTo(7);
  }

  @Test
  void cannotConsumeReservedUnits() {
    var p = product();
    p.receive(10, now);
    p.reserve(8, now);
    assertThatThrownBy(() -> p.adjust(-3, now)).hasMessageContaining("reserved");
    assertThat(p.getOnHand()).isEqualTo(10);
  }

  @Test
  void invalidCounterOperationsDoNotMutate() {
    var p = product();
    assertThatThrownBy(() -> p.receive(0, now)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> p.adjust(0, now)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> p.reserve(1, now)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> p.release(1, now)).isInstanceOf(IllegalStateException.class);
    assertThat(p.getOnHand()).isZero();
  }

  @Test
  void overflowCannotWrapStock() {
    var p = product();
    p.receive(Long.MAX_VALUE, now);
    assertThatThrownBy(() -> p.receive(1, now)).hasMessageContaining("limit");
    assertThat(p.getOnHand()).isEqualTo(Long.MAX_VALUE);
  }
}
