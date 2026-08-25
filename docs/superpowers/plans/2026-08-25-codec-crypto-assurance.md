# codec-crypto Assurance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the two agility seams (Provider injection, wrap-scheme tag) and the assurance program (KATs, fuzzing, mutation testing, static security analysis, threat model) to codec-crypto without changing the envelope wire format.

**Architecture:** Additive changes to `EnvelopeCodec` and `JceDataKeyProvider`; the GCM call extracted to package-private helpers so external test vectors exercise production code; build-time gates (PIT, SpotBugs+findsecbugs) scoped to codec-crypto's `ci` profile; Jazzer fuzz targets in regression mode in the suite plus an on-demand `fuzz` profile.

**Tech Stack:** Java 25 JCE only at compile scope. Test scope adds `jazzer-junit`. Build plugins: pitest-maven, spotbugs-maven-plugin + findsecbugs-plugin.

**Spec:** `docs/superpowers/specs/006-codec-crypto-assurance.md` (normative; amends 005). If plan and spec disagree, STOP and report.

## Global Constraints

- codec-crypto compile surface stays exactly `codec-core`. Test-scope additions are allowed but must pass the ci profile's `dependency:analyze-only` (failOnWarning) and enforcer gates — never edit their allowlists.
- Verification for every task: `./mvnw -Pci -B clean verify` (plain verify skips the gates). Docs tasks additionally: `python3 -m mkdocs build --strict` exit 0.
- No `@SuppressWarnings`, no SpotBugs exclusion filters, no star imports. Apache 2.0 license header on every new `.java` file — as the FIRST lines of the file, before `package`, copied from `codec-core/src/main/java/org/jwcarman/codec/spi/Codec.java`.
- Tests: `@Nested` capitalized phrases, `snake_case` sentence names. Never modify an existing assertion to make something pass.
- Format before committing: `./mvnw -q spotless:apply`. Commit trailer: `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`. Never push.
- Uniform cryptographic-failure message is exactly `"Unable to decrypt data"`.
- Wire format is unchanged: magic `4A 43`, version `01`, algorithm `01`, uint16 lengths, 12-byte nonce, 16-byte tag, min length 38, header = bytes `0..19+k+w`.
- Plugin/dependency versions given below are known-good at plan time; use the latest release available on Maven Central at implementation time if newer, and record the version used in the report.

---

### Task 1: Provider injection seam

**Files:**
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/EnvelopeCodec.java`
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/JceDataKeyProvider.java`
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/ProviderInjectionTest.java`

**Interfaces:**
- Consumes: existing `EnvelopeCodec.Builder`, existing `JceDataKeyProvider(String, Map)` and `(String, Map, SecureRandom)` constructors.
- Produces: `EnvelopeCodec.Builder.provider(java.security.Provider)`; `JceDataKeyProvider.builder(String currentKeyId, Map<String, SecretKey> keks)` → `JceDataKeyProvider.Builder` with `secureRandom(SecureRandom)`, `provider(Provider)`, `build()`. Both fail fast with `IllegalStateException` if the provider cannot supply the transform. Package-private `EnvelopeCodec.provider()` accessor is NOT added — later tasks pass the provider explicitly.

- [ ] **Step 1: Write the failing test**

```java
package org.jwcarman.codec.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.security.Provider;
import java.security.Security;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProviderInjectionTest {

  private static final Provider SUN_JCE = Security.getProvider("SunJCE");

  /** A provider that registers no services at all: every getInstance against it fails. */
  private static final class EmptyProvider extends Provider {
    private static final long serialVersionUID = 1L;

    EmptyProvider() {
      super("Empty", "1.0", "registers nothing");
    }
  }

  private static Map<String, SecretKey> keks() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 5);
    return Map.of("kek", new SecretKeySpec(kek, "AES"));
  }

  @Nested
  class Explicit_provider {
    @Test
    void sun_jce_selected_explicitly_round_trips_through_both_seams() {
      JceDataKeyProvider keys =
          JceDataKeyProvider.builder("kek", keks()).provider(SUN_JCE).build();
      EnvelopeCodec codec = EnvelopeCodec.builder(keys).provider(SUN_JCE).build();
      byte[] plaintext = "explicit provider".getBytes(UTF_8);
      assertThat(codec.decode(codec.encode(plaintext))).isEqualTo(plaintext);
    }

    @Test
    void jce_builder_secure_random_seam_is_honoured() {
      var random = new java.security.SecureRandom() {
        private byte next = 9;

        @Override
        public void nextBytes(byte[] bytes) {
          for (int i = 0; i < bytes.length; i++) {
            bytes[i] = next++;
          }
        }
      };
      JceDataKeyProvider a = JceDataKeyProvider.builder("kek", keks()).secureRandom(random).build();
      assertThat(a.newDataKey().key().getEncoded()[0]).isEqualTo((byte) 9);
    }
  }

  @Nested
  class Fail_fast {
    @Test
    void codec_builder_rejects_a_provider_without_aes_gcm() {
      JceDataKeyProvider keys = new JceDataKeyProvider("kek", keks());
      assertThatIllegalStateException()
          .isThrownBy(() -> EnvelopeCodec.builder(keys).provider(new EmptyProvider()).build())
          .withMessageContaining("AES/GCM/NoPadding");
    }

    @Test
    void jce_builder_rejects_a_provider_without_aes_wrap() {
      assertThatIllegalStateException()
          .isThrownBy(() -> JceDataKeyProvider.builder("kek", keks()).provider(new EmptyProvider()).build())
          .withMessageContaining("AESWrap");
    }
  }
}
```

- [ ] **Step 2: Run to verify it fails** — `./mvnw -q -pl codec-crypto test -Dtest=ProviderInjectionTest` — Expected: compilation failure (`provider(...)`, `builder(...)` missing).

- [ ] **Step 3: Implement**

In `EnvelopeCodec`:
- Add field `private final Provider provider;` (may be null), set from the builder.
- Add builder method:
  ```java
  public Builder provider(Provider provider) {
    this.provider = Objects.requireNonNull(provider, "provider must not be null");
    return this;
  }
  ```
- In `Builder.build()`, before constructing: call a new private static `checkTransform(GCM_TRANSFORM, provider)`:
  ```java
  private static void checkTransform(String transform, Provider provider) {
    try {
      newCipher(transform, provider);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(
          "provider " + (provider == null ? "<default>" : provider.getName())
              + " cannot supply " + transform, e);
    }
  }

  static Cipher newCipher(String transform, Provider provider) throws GeneralSecurityException {
    return provider == null ? Cipher.getInstance(transform) : Cipher.getInstance(transform, provider);
  }
  ```
- Replace both `Cipher.getInstance(GCM_TRANSFORM)` calls with `newCipher(GCM_TRANSFORM, provider)`.

In `JceDataKeyProvider`:
- Add field `private final Provider provider;`.
- Add a private canonical constructor `(String currentKeyId, Map<String, SecretKey> keks, SecureRandom random, Provider provider)` containing all existing validation, plus at the end `EnvelopeCodec.newCipher`-style fail-fast for `WRAP_TRANSFORM` (throw `IllegalStateException` naming `AESWrap` and the provider name). Have the two existing public constructors delegate with `provider = null`.
- Replace both `Cipher.getInstance(WRAP_TRANSFORM)` calls with a private `wrapCipher()` that uses the provider when set.
- Add the builder:
  ```java
  public static Builder builder(String currentKeyId, Map<String, SecretKey> keks) {
    return new Builder(currentKeyId, keks);
  }

  public static final class Builder {
    private final String currentKeyId;
    private final Map<String, SecretKey> keks;
    private SecureRandom random = new SecureRandom();
    private Provider provider;

    private Builder(String currentKeyId, Map<String, SecretKey> keks) {
      this.currentKeyId = Objects.requireNonNull(currentKeyId, "currentKeyId must not be null");
      this.keks = Objects.requireNonNull(keks, "keks must not be null");
    }

    public Builder secureRandom(SecureRandom random) {
      this.random = Objects.requireNonNull(random, "random must not be null");
      return this;
    }

    public Builder provider(Provider provider) {
      this.provider = Objects.requireNonNull(provider, "provider must not be null");
      return this;
    }

    public JceDataKeyProvider build() {
      return new JceDataKeyProvider(currentKeyId, keks, random, provider);
    }
  }
  ```
- Javadoc on both `provider(...)` methods: purpose (FIPS-validated or otherwise pinned provider selection per codec, no global provider ordering games), fail-fast behaviour, and that the default is JDK lookup.

- [ ] **Step 4: Run** — `./mvnw -q -pl codec-crypto test` — Expected: all tests PASS (existing suites unchanged).

- [ ] **Step 5: Full verify and commit** — `./mvnw -q spotless:apply && ./mvnw -Pci -B clean verify`, then `git add codec-crypto && git commit -m "Add JCE Provider injection to EnvelopeCodec and JceDataKeyProvider"`.

---

### Task 2: Wrap-scheme tag, DEK zeroing, vector re-freeze, spec amendments

**Files:**
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/JceDataKeyProvider.java`
- Modify: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/JceDataKeyProviderTest.java`
- Modify: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/EnvelopeCodecEncodeTest.java` (w expectation 40 → 41)
- Modify: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/WireFormatVectorTest.java` (re-bootstrap)
- Modify: `docs/superpowers/specs/005-codec-crypto.md` (three amendments)

**Interfaces:**
- Produces: `JceDataKeyProvider` blob = `[0x01][40-byte AES-KW payload]` (41 bytes). Package-private constant `JceDataKeyProvider.WRAP_SCHEME_AES_KW = 0x01` for tests.

- [ ] **Step 1: Write the failing tests** — add to `JceDataKeyProviderTest`, nested class `Wrap_scheme`:

```java
  @Nested
  class Wrap_scheme {
    @Test
    void wrapped_blob_carries_the_aes_kw_scheme_tag_and_is_41_bytes() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      byte[] wrapped = provider.newDataKey().wrapped();
      assertThat(wrapped).hasSize(41);
      assertThat(wrapped[0]).isEqualTo(JceDataKeyProvider.WRAP_SCHEME_AES_KW);
    }

    @Test
    void an_unknown_scheme_tag_is_rejected_with_the_uniform_message() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      byte[] wrapped = provider.newDataKey().wrapped();
      wrapped[0] = 0x7F;
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> provider.unwrap("kek", wrapped))
          .withMessage("Unable to decrypt data");
    }

    @Test
    void a_one_byte_blob_is_rejected_with_the_uniform_message() {
      JceDataKeyProvider provider = new JceDataKeyProvider("kek", Map.of("kek", aesKey((byte) 1)));
      assertThatExceptionOfType(DecryptionException.class)
          .isThrownBy(() -> provider.unwrap("kek", new byte[] {JceDataKeyProvider.WRAP_SCHEME_AES_KW}))
          .withMessage("Unable to decrypt data");
    }
  }
```

Also change `EnvelopeCodecEncodeTest` line asserting `w` from `isEqualTo(40)` to `isEqualTo(41); // scheme tag + AESWrap of a 32-byte DEK`.

- [ ] **Step 2: Run to verify it fails** — `./mvnw -q -pl codec-crypto test` — Expected: the three new tests and the encode `w` test fail; `WireFormatVectorTest` fails (vector changes).

- [ ] **Step 3: Implement** in `JceDataKeyProvider`:

```java
  static final byte WRAP_SCHEME_AES_KW = 0x01;
```
`newDataKey`:
```java
    byte[] dekBytes = new byte[DEK_LENGTH_BYTES];
    random.nextBytes(dekBytes);
    SecretKey dek = new SecretKeySpec(dekBytes, AES);
    Arrays.fill(dekBytes, (byte) 0); // SecretKeySpec holds its own copy
    try {
      Cipher cipher = wrapCipher();
      cipher.init(Cipher.WRAP_MODE, keks.get(currentKeyId));
      byte[] payload = cipher.wrap(dek);
      byte[] blob = new byte[1 + payload.length];
      blob[0] = WRAP_SCHEME_AES_KW;
      System.arraycopy(payload, 0, blob, 1, payload.length);
      return new DataKey(currentKeyId, dek, blob);
    } catch (GeneralSecurityException e) {
      throw new EncryptionException("Unable to wrap data key", e);
    }
```
`unwrap`, after the KEK lookup:
```java
    if (wrapped.length < 2 || wrapped[0] != WRAP_SCHEME_AES_KW) {
      throw DecryptionException.cryptographic(null);
    }
    byte[] payload = Arrays.copyOfRange(wrapped, 1, wrapped.length);
    ... cipher.unwrap(payload, AES, Cipher.SECRET_KEY) ...
```
Javadoc: document the blob layout and that scheme `0x02+` are reserved for future wrap algorithms.

- [ ] **Step 4: Re-bootstrap the frozen vector** — run `./mvnw -q -pl codec-crypto test -Dtest=WireFormatVectorTest`; copy the actual hex into `FROZEN_VECTOR_HEX`; sanity-check by eye: must begin `4a4301 01 0003 6b656b 0029 01` (wrapped length 41, then the scheme byte). Total length is now 95 bytes (190 hex chars). Update the test's comment about the byte layout if it states 94/40.

- [ ] **Step 5: Amend spec 005** — (a) JceDataKeyProvider section: blob layout `[scheme][AES-KW payload]`, scheme 0x01, rejection rule; (b) EnvelopeCodec and JceDataKeyProvider sections: provider injection per spec 006 §1.1; (c) Algorithm section: add the "Future suites" paragraph verbatim in meaning from spec 006 §1.3. Also update the overhead example ("~90 bytes for JCE") to reflect w=41.

- [ ] **Step 6: Full verify and commit** — `./mvnw -q spotless:apply && ./mvnw -Pci -B clean verify`; `git add -A && git commit -m "Tag JceDataKeyProvider wrapped blobs with a scheme byte and zero transient DEK bytes"`.

---

### Task 3: Known-answer tests

**Files:**
- Modify: `codec-crypto/src/main/java/org/jwcarman/codec/crypto/EnvelopeCodec.java` (extract `gcmEncrypt` / `gcmDecrypt`)
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/GcmKnownAnswerTest.java`
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/AesKeyWrapKnownAnswerTest.java`

**Interfaces:**
- Produces (package-private, static, on `EnvelopeCodec`):
  ```java
  static byte[] gcmEncrypt(Provider provider, SecretKey key, byte[] nonce, byte[] headerAad, byte[] extraAad, byte[] plaintext) throws GeneralSecurityException
  static byte[] gcmDecrypt(Provider provider, SecretKey key, byte[] nonce, byte[] headerAad, byte[] extraAad, byte[] data, int offset, int length) throws GeneralSecurityException
  ```
  `extraAad` may be null. Both use `newCipher(GCM_TRANSFORM, provider)` and `GCMParameterSpec(TAG_LENGTH_BITS, nonce)`; `updateAAD(headerAad)` then `updateAAD(extraAad)` when non-null.

- [ ] **Step 1: Refactor encode/decode to use the helpers** (behaviour-preserving; existing suite is the regression net):
  - encode: `byte[] sealed = gcmEncrypt(provider, dataKey.key(), nonce, Arrays.copyOf(message, headerLength), aad, value); System.arraycopy(sealed, 0, message, headerLength, sealed.length);` — NOTE: the header AAD must be the fully-written header; keep the header writes before this call.
  - decode: `return gcmDecrypt(provider, dek, nonce, Arrays.copyOf(bytes, headerLength), aad, bytes, headerLength, bytes.length - headerLength);`
  Run `./mvnw -q -pl codec-crypto test` — all existing tests, including the frozen vector, must still pass (proves the refactor preserved bytes).

- [ ] **Step 2: Obtain the NIST vectors.** Download `https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Algorithm-Validation-Program/documents/mac/gcmtestvectors.zip`, extract `gcmEncryptExtIV256.rsp` and `gcmDecrypt256.rsp`. From the encrypt file select the section `[Keylen = 256] [IVlen = 96] [PTlen = 0] [AADlen = 128] [Taglen = 128]` (Count 0 and 1) and `[PTlen = 128] [AADlen = 128] [Taglen = 128]` (Count 0–3): six vectors total. From the decrypt file select two vectors marked `FAIL` in the `[Keylen = 256] [IVlen = 96] [PTlen = 128] [AADlen = 128] [Taglen = 128]` section. Record file name, section header, and Count in a comment above each vector. Do not paraphrase hex; copy it.

- [ ] **Step 3: Write the GCM KAT**

```java
package org.jwcarman.codec.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.AEADBadTagException;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** NIST CAVP GCM vectors (gcmtestvectors.zip) driven through the module's own GCM helpers. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GcmKnownAnswerTest {

  private record Vector(String source, String key, String iv, String aad, String pt, String ct, String tag) {}

  private static final HexFormat HEX = HexFormat.of();

  // gcmEncryptExtIV256.rsp — fill each field verbatim from the file; keep the source string exact.
  private static final List<Vector> ENCRYPT = List.of(
      new Vector("gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=0][AADlen=128][Taglen=128] Count=0", "…", "…", "…", "", "", "…"),
      new Vector("gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=0][AADlen=128][Taglen=128] Count=1", "…", "…", "…", "", "", "…"),
      new Vector("gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=0", "…", "…", "…", "…", "…", "…"),
      new Vector("gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=1", "…", "…", "…", "…", "…", "…"),
      new Vector("gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=2", "…", "…", "…", "…", "…", "…"),
      new Vector("gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=3", "…", "…", "…", "…", "…", "…"));

  // gcmDecrypt256.rsp — two vectors whose expected result is FAIL.
  private static final List<Vector> DECRYPT_FAIL = List.of(
      new Vector("gcmDecrypt256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=…", "…", "…", "…", "", "…", "…"),
      new Vector("gcmDecrypt256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=…", "…", "…", "…", "", "…", "…"));

  @Nested
  class Encrypt_vectors {
    @Test
    void every_nist_encrypt_vector_produces_the_published_ciphertext_and_tag() throws GeneralSecurityException {
      for (Vector v : ENCRYPT) {
        byte[] out = EnvelopeCodec.gcmEncrypt(null, new SecretKeySpec(HEX.parseHex(v.key()), "AES"),
            HEX.parseHex(v.iv()), HEX.parseHex(v.aad()), null, HEX.parseHex(v.pt()));
        assertThat(HEX.formatHex(out)).as(v.source()).isEqualTo(v.ct() + v.tag());
      }
    }

    @Test
    void every_nist_encrypt_vector_decrypts_back_to_the_plaintext() throws GeneralSecurityException {
      for (Vector v : ENCRYPT) {
        byte[] data = HEX.parseHex(v.ct() + v.tag());
        byte[] pt = EnvelopeCodec.gcmDecrypt(null, new SecretKeySpec(HEX.parseHex(v.key()), "AES"),
            HEX.parseHex(v.iv()), HEX.parseHex(v.aad()), null, data, 0, data.length);
        assertThat(HEX.formatHex(pt)).as(v.source()).isEqualTo(v.pt());
      }
    }
  }

  @Nested
  class Decrypt_fail_vectors {
    @Test
    void every_nist_fail_vector_is_rejected_at_tag_verification() {
      for (Vector v : DECRYPT_FAIL) {
        byte[] data = HEX.parseHex(v.ct() + v.tag());
        assertThatExceptionOfType(AEADBadTagException.class).as(v.source())
            .isThrownBy(() -> EnvelopeCodec.gcmDecrypt(null, new SecretKeySpec(HEX.parseHex(v.key()), "AES"),
                HEX.parseHex(v.iv()), HEX.parseHex(v.aad()), null, data, 0, data.length));
      }
    }
  }

  @Nested
  class Aad_split_semantics {
    @Test
    void header_and_extra_aad_are_equivalent_to_their_concatenation() throws GeneralSecurityException {
      Vector v = ENCRYPT.get(2);
      byte[] aad = HEX.parseHex(v.aad());
      byte[] head = java.util.Arrays.copyOf(aad, 5);
      byte[] tail = java.util.Arrays.copyOfRange(aad, 5, aad.length);
      byte[] split = EnvelopeCodec.gcmEncrypt(null, new SecretKeySpec(HEX.parseHex(v.key()), "AES"),
          HEX.parseHex(v.iv()), head, tail, HEX.parseHex(v.pt()));
      assertThat(HEX.formatHex(split)).isEqualTo(v.ct() + v.tag());
    }
  }
}
```

The `"…"` placeholders are filled with the exact NIST hex in Step 2 — this is the plan's only sanctioned placeholder besides the frozen vector.

- [ ] **Step 4: Write the AES-KW KAT** (RFC 3394 §4.6 — "Wrap 256 bits of Key Data with a 256-bit KEK"; verify the three hex strings against https://www.rfc-editor.org/rfc/rfc3394#section-4.6 before committing):

```java
package org.jwcarman.codec.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** RFC 3394 §4.6 known-answer vector driven through JceDataKeyProvider's wrap path. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AesKeyWrapKnownAnswerTest {

  private static final HexFormat HEX = HexFormat.of();
  private static final String KEK = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
  private static final String KEY_DATA = "00112233445566778899AABBCCDDEEFF000102030405060708090A0B0C0D0E0F";
  private static final String EXPECTED =
      "28C9F404C4B810F4CBCCB35CFB87F8263F5786E2D80ED326CBC7F0E71A99F43BFB988B9B7A02DD21";

  @Test
  void wrapping_the_rfc_3394_key_data_under_the_rfc_kek_yields_the_published_ciphertext() {
    byte[] keyData = HEX.parseHex(KEY_DATA);
    SecureRandom fixed = new SecureRandom() {
      @Override
      public void nextBytes(byte[] bytes) {
        System.arraycopy(keyData, 0, bytes, 0, bytes.length);
      }
    };
    JceDataKeyProvider provider = JceDataKeyProvider.builder(
            "kek", Map.of("kek", new SecretKeySpec(HEX.parseHex(KEK), "AES")))
        .secureRandom(fixed)
        .build();
    byte[] blob = provider.newDataKey().wrapped();
    assertThat(blob[0]).isEqualTo(JceDataKeyProvider.WRAP_SCHEME_AES_KW);
    assertThat(HEX.formatHex(Arrays.copyOfRange(blob, 1, blob.length)))
        .isEqualToIgnoringCase(EXPECTED);
  }

  @Test
  void the_published_ciphertext_unwraps_to_the_rfc_key_data() {
    JceDataKeyProvider provider =
        new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(HEX.parseHex(KEK), "AES")));
    byte[] payload = HEX.parseHex(EXPECTED);
    byte[] blob = new byte[1 + payload.length];
    blob[0] = JceDataKeyProvider.WRAP_SCHEME_AES_KW;
    System.arraycopy(payload, 0, blob, 1, payload.length);
    assertThat(HEX.formatHex(provider.unwrap("kek", blob).getEncoded()))
        .isEqualToIgnoringCase(KEY_DATA);
  }
}
```

- [ ] **Step 5: Run** — `./mvnw -q -pl codec-crypto test` — Expected: PASS. A KAT failure is a real finding about how the module uses the JDK — STOP with NEEDS_CONTEXT and the failing vector; do not adjust vectors.

- [ ] **Step 6: Full verify and commit** — `./mvnw -q spotless:apply && ./mvnw -Pci -B clean verify`; `git add -A && git commit -m "Add NIST GCM and RFC 3394 known-answer tests through production helpers"`.

---

### Task 4: Decoder fuzzing with Jazzer

**Files:**
- Modify: `codec-crypto/pom.xml` (test dep; `fuzz` profile)
- Test: `codec-crypto/src/test/java/org/jwcarman/codec/crypto/EnvelopeCodecFuzzTest.java`
- Create: seed corpus under `codec-crypto/src/test/resources/org/jwcarman/codec/crypto/EnvelopeCodecFuzzTestInputs/decode_only_throws_the_documented_exceptions/` — at least: `frozen-vector` (the current frozen vector bytes), `empty` (0 bytes), `magic-only` (`4A 43`), `zeros-38` (38 zero bytes), `valid-magic-short` (`4A 43` + 8 zero bytes).

- [ ] **Step 1: Add the dependency** (test scope) to `codec-crypto/pom.xml`:
```xml
        <dependency>
            <groupId>com.code-intelligence</groupId>
            <artifactId>jazzer-junit</artifactId>
            <version>0.24.0</version>
            <scope>test</scope>
        </dependency>
```
Then run `./mvnw -Pci -B -pl codec-crypto -am clean verify` BEFORE writing tests: if `dependency:analyze-only` reports jazzer as unused-declared (it will until a test uses it) that is expected at this step only; if enforcer `requireUpperBoundDeps`/`dependencyConvergence` fails on jazzer's transitives, STOP with NEEDS_CONTEXT and the output — do not touch allowlists or add exclusions without a ruling.

- [ ] **Step 2: Write the fuzz targets**

```java
package org.jwcarman.codec.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;

/**
 * Fuzz targets for the decode path. In the normal test run Jazzer replays the committed seed
 * corpus (regression mode); with JAZZER_FUZZ=1 (the {@code fuzz} profile) it fuzzes for real.
 */
class EnvelopeCodecFuzzTest {

  private static EnvelopeCodec codec() {
    byte[] kek = new byte[32];
    java.util.Arrays.fill(kek, (byte) 3);
    return EnvelopeCodec.builder(
            new JceDataKeyProvider("kek", Map.of("kek", new SecretKeySpec(kek, "AES"))))
        .build();
  }

  @FuzzTest
  void decode_only_throws_the_documented_exceptions(byte[] input) {
    try {
      codec().decode(input);
    } catch (DecryptionException | KeyAccessException expected) {
      // documented outcomes
    }
    // any other Throwable escapes and Jazzer records it as a finding
  }

  @FuzzTest
  void mutated_ciphertext_is_rejected_and_unmutated_round_trips(FuzzedDataProvider data) {
    EnvelopeCodec codec = codec();
    byte[] plaintext = data.consumeBytes(256);
    byte[] message = codec.encode(plaintext);
    int flips = data.consumeInt(0, 4);
    boolean mutated = false;
    for (int i = 0; i < flips && message.length > 0; i++) {
      int index = data.consumeInt(0, message.length - 1);
      byte mask = data.consumeByte();
      if (mask != 0) {
        message[index] ^= mask;
        mutated = true;
      }
    }
    if (!mutated) {
      assertThat(codec.decode(message)).isEqualTo(plaintext);
      return;
    }
    try {
      byte[] out = codec.decode(message);
      // A mutation that is accepted must still yield the original plaintext — anything else is a forgery.
      assertThat(out).isEqualTo(plaintext);
    } catch (DecryptionException expected) {
      // documented outcome
    }
  }
}
```

Note: the second target's "accepted mutation must equal original" branch is theoretically reachable only if two masks cancel; it is kept because a forgery accepted with different plaintext is exactly the finding we want.

- [ ] **Step 3: Seed corpus** — write the five files listed above as raw bytes (use a tiny Java/`printf` snippet; no text encoding). The frozen vector bytes come from `WireFormatVectorTest.FROZEN_VECTOR_HEX`.

- [ ] **Step 4: `fuzz` profile** in `codec-crypto/pom.xml`:
```xml
    <profiles>
        <profile>
            <id>fuzz</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <configuration>
                            <test>EnvelopeCodecFuzzTest</test>
                            <environmentVariables>
                                <JAZZER_FUZZ>1</JAZZER_FUZZ>
                            </environmentVariables>
                            <argLine>@{jacocoArgLine} -Djazzer.max_duration=120s</argLine>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
```
(Confirm the current Jazzer duration-limit mechanism in its docs for the version used — it may be `-Djazzer.max_duration` or `--max_total_time`; use what the version supports and record it.)

- [ ] **Step 5: Regression run** — `./mvnw -q -pl codec-crypto test -Dtest=EnvelopeCodecFuzzTest` — Expected: PASS (corpus replay).

- [ ] **Step 6: Live fuzz run** — `./mvnw -pl codec-crypto -Pfuzz test` for the configured 120s. Expected: no findings. If Jazzer reports a crash: it writes the crashing input to a `crash-*` file — copy it into the corpus directory, diagnose, and STOP with NEEDS_CONTEXT including the stack trace if the fix is not a one-liner in the decoder's validation. Record the run summary (executions, coverage) in the report.

- [ ] **Step 7: Full verify and commit** — `./mvnw -q spotless:apply && ./mvnw -Pci -B clean verify`; `git add -A && git commit -m "Add Jazzer fuzz targets for the decode path with a seed corpus and fuzz profile"`.

---

### Task 5: Mutation testing with PIT

**Files:**
- Modify: `codec-crypto/pom.xml` (ci profile: pitest)
- Modify: tests as needed to kill survivors

- [ ] **Step 1: Add PIT to codec-crypto's ci profile** (a `<profile><id>ci</id>` block in the module pom, merged with the parent's by Maven):
```xml
        <profile>
            <id>ci</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.pitest</groupId>
                        <artifactId>pitest-maven</artifactId>
                        <version>1.20.1</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.pitest</groupId>
                                <artifactId>pitest-junit5-plugin</artifactId>
                                <version>1.2.3</version>
                            </dependency>
                        </dependencies>
                        <configuration>
                            <targetClasses>
                                <param>org.jwcarman.codec.crypto.*</param>
                            </targetClasses>
                            <excludedTestClasses>
                                <param>org.jwcarman.codec.crypto.EnvelopeCodecFuzzTest</param>
                            </excludedTestClasses>
                            <mutationThreshold>85</mutationThreshold>
                            <coverageThreshold>90</coverageThreshold>
                            <timestampedReports>false</timestampedReports>
                            <outputFormats>
                                <param>XML</param>
                                <param>HTML</param>
                            </outputFormats>
                            <failWhenNoMutations>true</failWhenNoMutations>
                        </configuration>
                        <executions>
                            <execution>
                                <id>mutation-coverage</id>
                                <phase>verify</phase>
                                <goals>
                                    <goal>mutationCoverage</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
```

- [ ] **Step 2: First run** — `./mvnw -Pci -B -pl codec-crypto -am clean verify`. Read `codec-crypto/target/pit-reports/index.html` (or the XML). Record the initial mutation score and line coverage in the report.

- [ ] **Step 3: Kill survivors** — for each surviving mutant, add or sharpen a test in the relevant existing test class that fails against the mutant (typical survivors: boundary conditions in `decode`'s bounds checks, `BoundedDataKeyStrategy` comparisons, builder null-checks). Never lower the thresholds. For a mutant that is provably equivalent (no observable behaviour change), leave a comment in the test class nearest the code naming the mutation and why it is equivalent, and list it in the report.

- [ ] **Step 4: Confirm** — re-run Step 2 until the build passes the thresholds. Record final score, coverage, and the survivor list with disposition.

- [ ] **Step 5: Commit** — `./mvnw -q spotless:apply`; `git add -A && git commit -m "Gate codec-crypto on PIT mutation coverage and strengthen tests to meet it"`.

---

### Task 6: SpotBugs + find-sec-bugs

**Files:**
- Modify: `codec-crypto/pom.xml` (ci profile: spotbugs)
- Modify: main sources as needed to resolve findings

- [ ] **Step 1: Add SpotBugs to codec-crypto's ci profile** (same `<profile><id>ci</id>` block as Task 5):
```xml
                    <plugin>
                        <groupId>com.github.spotbugs</groupId>
                        <artifactId>spotbugs-maven-plugin</artifactId>
                        <version>4.9.3.0</version>
                        <configuration>
                            <effort>Max</effort>
                            <threshold>Low</threshold>
                            <failOnError>true</failOnError>
                            <includeTests>false</includeTests>
                            <plugins>
                                <plugin>
                                    <groupId>com.h3xstream.findsecbugs</groupId>
                                    <artifactId>findsecbugs-plugin</artifactId>
                                    <version>1.14.0</version>
                                </plugin>
                            </plugins>
                        </configuration>
                        <executions>
                            <execution>
                                <id>spotbugs-check</id>
                                <phase>verify</phase>
                                <goals>
                                    <goal>check</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
```

- [ ] **Step 2: Run** — `./mvnw -Pci -B -pl codec-crypto -am clean verify`. For each finding: fix the code (e.g. a genuine EI_EXPOSE_REP on a getter → return a copy; a PREDICTABLE_RANDOM on a `SecureRandom` misuse → it is not one, so the code must be restructured so the analyzer sees the `SecureRandom` type directly). No `@SuppressFBWarnings`, no `spotbugs-exclude.xml`. If a finding is a demonstrable false positive with no structural fix, STOP with NEEDS_CONTEXT quoting the finding — a filter requires a controller ruling and the maintainer's explicit exception.

- [ ] **Step 3: Confirm clean** — re-run until `spotbugs:check` passes with zero bugs at threshold Low. Record every finding and its resolution in the report.

- [ ] **Step 4: Commit** — `./mvnw -q spotless:apply`; `git add -A && git commit -m "Gate codec-crypto on SpotBugs with find-sec-bugs at threshold Low"`.

---

### Task 7: Threat model, docs, CHANGELOG

**Files:**
- Create: `docs/guides/threat-model.md`
- Modify: `mkdocs.yml` (nav: after Encryption)
- Modify: `docs/guides/encryption.md` (provider injection; JCE blob layout; assurance section)
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Write `docs/guides/threat-model.md`** with these H2 sections, plain prose, admonitions for the sharp edges: Assets (plaintext, DEKs, KEKs, keyId→KEK trust set); Trust boundaries (EnvelopeCodec ↔ DataKeyProvider ↔ KMS ↔ consumer datastore ↔ logs); Attacker capabilities considered (reads ciphertext; writes/substitutes ciphertext in the datastore; holds a KEK the application is *permitted* but not *intended* to use; reads application logs; snapshots and clones the VM); Defended — with the mechanism for each (confidentiality/integrity via AES-256-GCM with header-as-AAD; keyId admission before unwrap; uniform cryptographic failure; bounds before allocation; fresh-DEK default; KeyAccessException vs DecryptionException); Not defended — with the consumer-side mitigation (ciphertext substitution between records under one codec/KEK → per-context codec or in-plaintext identity; timing side channel; nonce reuse under cloned RNG state with BoundedDataKeyStrategy → prefer direct or roll on resume; a provider that violates its contract; key commitment — reserved 0x02); Assurance status (KATs against NIST/RFC vectors, fuzzing with seed corpus and `-Pfuzz`, PIT thresholds, SpotBugs+findsecbugs, adversarial review history); Independent review checklist — a bulleted list an outside cryptographer should verify: AAD span = bytes 0..19+k+w plus instance AAD on both paths; admission strictly precedes unwrap; all bounds checked before allocation and before any provider call; the uniform failure message is shared by every cryptographic rejection; nonce is 12 random bytes per encode from `SecureRandom`; `JceDataKeyProvider` uses AES-KW with a 256-bit KEK and rejects unknown scheme bytes; `BoundedDataKeyStrategy` cap ≤ 2^24 and monotonic-subtraction expiry; no key material in any `toString` or exception. End with "Where next" links to encryption.md and the SECURITY.md policy.

- [ ] **Step 2: Update `encryption.md`** — add a "Choosing a JCE provider" subsection (builder `.provider(...)` on both types, fail-fast behaviour, FIPS note); update the JceDataKeyProvider description with the `[scheme][payload]` blob layout; add an "Assurance" subsection pointing at the threat model and naming the gates (`-Pci` runs PIT and SpotBugs for this module; `-Pfuzz` fuzzes). Every name grepped against source first.

- [ ] **Step 3: nav** — in `mkdocs.yml` Guides, after `Encryption`: `      - Threat Model: guides/threat-model.md`.

- [ ] **Step 4: CHANGELOG** under `## [Unreleased]`: extend the existing `### Added` codec-crypto bullet or add bullets for: JCE `Provider` injection; wrap-scheme tag; known-answer tests, fuzz targets, mutation and static-analysis gates; threat model page.

- [ ] **Step 5: Verify and commit** — `python3 -m mkdocs build --strict && ./mvnw -Pci -B clean verify`; `git add -A && git commit -m "Add the codec-crypto threat model and document the assurance program"`.

---

## Self-review record

- Spec coverage: 006 §1.1 → T1; §1.2 → T2; §1.3 → T2 step 5; §2.1 → T3; §2.2 → T4; §2.3 → T5; §2.4 → T6; §2.5 → T7; DoD CHANGELOG → T7.
- Placeholders: two sanctioned — NIST vector hex (T3, with an exact provenance procedure) and the frozen-vector re-bootstrap (T2, with a prefix sanity check). Version numbers carry an explicit "use latest, record it" rule.
- Type consistency: `newCipher(String, Provider)` defined in T1 and used by T3's helpers; `WRAP_SCHEME_AES_KW` defined in T2 and used by T3; `gcmEncrypt`/`gcmDecrypt` signatures identical between T3's Produces block, helpers, and KAT calls; `JceDataKeyProvider.builder(...).secureRandom(...)` from T1 used by T3.
