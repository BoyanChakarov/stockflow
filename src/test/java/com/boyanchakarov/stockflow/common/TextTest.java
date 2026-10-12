package com.boyanchakarov.stockflow.common;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextTest {
  @Test
  void canonicalTextPreservesCustomerCase() {
    assertThat(Text.sku(" mug-blue ")).isEqualTo("MUG-BLUE");
    assertThat(Text.username(" ADMIN ")).isEqualTo("admin");
    assertThat(Text.trim(" Atelier Forma ")).isEqualTo("Atelier Forma");
    assertThat(Text.optional("   ")).isNull();
  }

  @Test
  void fingerprintIsStableAndPayloadSensitive() {
    assertThat(Idempotency.fingerprint("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(Idempotency.fingerprint("abd")).isNotEqualTo(Idempotency.fingerprint("abc"));
  }
}
