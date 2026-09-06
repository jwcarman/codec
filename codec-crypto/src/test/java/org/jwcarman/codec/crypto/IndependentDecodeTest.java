/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.codec.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Decodes {@link WireFormatVectorTest}'s frozen vector without the codec: the header is parsed by
 * hand from spec 005's wire-format table, the DEK is unwrapped with a {@code Cipher} directly, and
 * GCM is driven directly with the AAD the spec names. {@code WireFormatVectorTest} proves the codec
 * still produces and reads the vector; this proves the vector means what the spec says it means, so
 * a change to the codec's own AAD-span or header logic cannot hide behind a self-consistent round
 * trip.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class IndependentDecodeTest {

  private static final byte[] VECTOR =
      HexFormat.of().parseHex(WireFormatVectorTest.FROZEN_VECTOR_HEX);
  private static final SecretKey ZERO_KEK = new SecretKeySpec(new byte[32], "AES");

  /** Spec 005 wire format, parsed by hand: offsets are the table's, not the codec's constants. */
  private record Header(int keyIdLength, String keyId, byte[] wrapped, byte[] nonce, int length) {
    static Header parse(byte[] m) {
      int k = ((m[4] & 0xFF) << 8) | (m[5] & 0xFF);
      String keyId = new String(m, 6, k, UTF_8);
      int w = ((m[6 + k] & 0xFF) << 8) | (m[7 + k] & 0xFF);
      byte[] wrapped = Arrays.copyOfRange(m, 8 + k, 8 + k + w);
      int length = 20 + k + w;
      byte[] nonce = Arrays.copyOfRange(m, length - 12, length);
      return new Header(k, keyId, wrapped, nonce, length);
    }
  }

  private static byte[] sequence(int from, int count) {
    byte[] out = new byte[count];
    for (int i = 0; i < count; i++) {
      out[i] = (byte) (from + i);
    }
    return out;
  }

  private static SecretKey unwrapWithAesKw(byte[] wrapped) throws GeneralSecurityException {
    Cipher kw = Cipher.getInstance("AES/KW/NoPadding");
    kw.init(Cipher.UNWRAP_MODE, ZERO_KEK);
    return (SecretKey)
        kw.unwrap(Arrays.copyOfRange(wrapped, 1, wrapped.length), "AES", Cipher.SECRET_KEY);
  }

  private static byte[] gcmOpen(SecretKey dek, byte[] nonce, byte[] aad, byte[] body)
      throws GeneralSecurityException {
    Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
    gcm.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(128, nonce));
    if (aad != null) {
      gcm.updateAAD(aad);
    }
    return gcm.doFinal(body);
  }

  @Nested
  class The_header {

    @Test
    void is_magic_version_and_algorithm_as_the_spec_states() {
      assertThat(Arrays.copyOf(VECTOR, 4)).containsExactly('J', 'C', 0x01, 0x01);
    }

    @Test
    void carries_the_key_id_and_a_scheme_tagged_wrapped_key() {
      Header h = Header.parse(VECTOR);

      assertThat(h.keyIdLength()).isEqualTo(3);
      assertThat(h.keyId()).isEqualTo("kek");
      assertThat(h.wrapped()).hasSize(41);
      assertThat(h.wrapped()[0]).isEqualTo((byte) 0x01);
      assertThat(h.length()).isEqualTo(64);
    }

    @Test
    void ends_with_the_nonce_the_test_rng_produced_after_the_dek() {
      assertThat(Header.parse(VECTOR).nonce()).isEqualTo(sequence(0x20, 12));
    }
  }

  @Nested
  class The_wrapped_key {

    @Test
    void unwraps_under_the_zero_kek_with_a_plain_aes_kw_cipher_to_the_test_rngs_first_32_bytes()
        throws GeneralSecurityException {
      SecretKey dek = unwrapWithAesKw(Header.parse(VECTOR).wrapped());

      assertThat(dek.getEncoded()).isEqualTo(sequence(0, 32));
    }
  }

  @Nested
  class The_ciphertext {

    @Test
    void opens_with_plain_gcm_under_the_unwrapped_dek_and_the_whole_header_as_aad()
        throws GeneralSecurityException {
      Header h = Header.parse(VECTOR);
      SecretKey dek = unwrapWithAesKw(h.wrapped());
      byte[] aad = Arrays.copyOf(VECTOR, h.length());
      byte[] body = Arrays.copyOfRange(VECTOR, h.length(), VECTOR.length);

      assertThat(gcmOpen(dek, h.nonce(), aad, body)).isEqualTo("codec-crypto v1".getBytes(UTF_8));
    }

    @Test
    void is_rejected_under_every_other_aad_span() throws GeneralSecurityException {
      Header h = Header.parse(VECTOR);
      SecretKey dek = unwrapWithAesKw(h.wrapped());
      byte[] body = Arrays.copyOfRange(VECTOR, h.length(), VECTOR.length);
      byte[][] wrongSpans = {
        null, // no AAD at all
        Arrays.copyOfRange(VECTOR, 2, h.length()), // header minus the magic
        Arrays.copyOf(VECTOR, h.length() - 12), // header minus the nonce
        Arrays.copyOf(VECTOR, h.length() + 1), // header plus one ciphertext byte
      };

      for (byte[] aad : wrongSpans) {
        assertThatExceptionOfType(AEADBadTagException.class)
            .isThrownBy(() -> gcmOpen(dek, h.nonce(), aad, body));
      }
    }
  }
}
