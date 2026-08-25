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

  private record Vector(
      String source, String key, String iv, String aad, String pt, String ct, String tag) {}

  private static final HexFormat HEX = HexFormat.of();

  // gcmEncryptExtIV256.rsp — fill each field verbatim from the file; keep the source string exact.
  private static final List<Vector> ENCRYPT =
      List.of(
          new Vector(
              "gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=0][AADlen=128][Taglen=128] Count=0",
              "78dc4e0aaf52d935c3c01eea57428f00ca1fd475f5da86a49c8dd73d68c8e223",
              "d79cf22d504cc793c3fb6c8a",
              "b96baa8c1c75a671bfb2d08d06be5f36",
              "",
              "",
              "3e5d486aa2e30b22e040b85723a06e76"),
          new Vector(
              "gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=0][AADlen=128][Taglen=128] Count=1",
              "4457ff33683cca6ca493878bdc00373893a9763412eef8cddb54f91318e0da88",
              "699d1f29d7b8c55300bb1fd2",
              "6749daeea367d0e9809e2dc2f309e6e3",
              "",
              "",
              "d60c74d2517fde4a74e0cd4709ed43a9"),
          new Vector(
              "gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=0",
              "92e11dcdaa866f5ce790fd24501f92509aacf4cb8b1339d50c9c1240935dd08b",
              "ac93a1a6145299bde902f21a",
              "1e0889016f67601c8ebea4943bc23ad6",
              "2d71bcfa914e4ac045b2aa60955fad24",
              "8995ae2e6df3dbf96fac7b7137bae67f",
              "eca5aa77d51d4a0a14d9c51e1da474ab"),
          new Vector(
              "gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=1",
              "7da3bccaffb3464178ca7c722379836db50ce0bfb47640b9572163865332e486",
              "c04fd2e701c3dc62b68738b3",
              "fec0311013202e4ffdc4204926ae0ddf",
              "fd671cab1ee21f0df6bb610bf94f0e69",
              "6be61b17b7f7d494a7cdf270562f37ba",
              "5e702a38323fe1160b780d17adad3e96"),
          new Vector(
              "gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=2",
              "a359b9584beec189527f8842dda6b6d4c6a5db2f889635715fa3bcd7967c0a71",
              "8616c4cde11b34a944caba32",
              "e1796fca20cb3d3ab0ade69b2a18891e",
              "33a46b7539d64c6e1bdb91ba221e3007",
              "b0d316e95f3f3390ba10d0274965c62b",
              "aeaedcf8a012cc32ef25a62790e9334c"),
          new Vector(
              "gcmEncryptExtIV256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=3",
              "8c83238e7b3b58278200b54940d779d0a0750673aab0bf2f5808dd15dc1a8c49",
              "70f8f4ebe408f61a35077956",
              "e1cbf83924f1b8d1014b97db56c25a15",
              "6e57f8572dd5b2247410f0d4c7424186",
              "4a11acb9611251df01f79f16f8201ffb",
              "9732be4ad0569586753d90fabb06f62c"));

  // gcmDecrypt256.rsp — two vectors whose expected result is FAIL.
  private static final List<Vector> DECRYPT_FAIL =
      List.of(
          new Vector(
              "gcmDecrypt256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=2",
              "9a0343f850a6427120f764789ffec6d237447b898fbf51d2182f065d3861497d",
              "3deef6f453dd70d92143adcd",
              "dbb8226a624520863db6897017b2a4f8",
              "",
              "e93165935ac18e3a2845d15fe31a9286",
              "f5fc50d18766bc3d9e16dd136d45816b"),
          new Vector(
              "gcmDecrypt256.rsp [Keylen=256][IVlen=96][PTlen=128][AADlen=128][Taglen=128] Count=4",
              "2a765ceac97265c15209eea90bea85cd9586b972160502ff592a306dc017e6b9",
              "62c545d9d4e3c7acb66b4bf1",
              "7d12474e23dc233bc6312d4d5b2deee4",
              "",
              "ae0594a7b66d3a958e4e6212d3288f91",
              "ec9aa846d185cc0f43d392240cd6e2c4"));

  @Nested
  class Encrypt_vectors {
    @Test
    void every_nist_encrypt_vector_produces_the_published_ciphertext_and_tag()
        throws GeneralSecurityException {
      for (Vector v : ENCRYPT) {
        byte[] out =
            EnvelopeCodec.gcmEncrypt(
                null,
                new SecretKeySpec(HEX.parseHex(v.key()), "AES"),
                HEX.parseHex(v.iv()),
                HEX.parseHex(v.aad()),
                null,
                HEX.parseHex(v.pt()));
        assertThat(HEX.formatHex(out)).as(v.source()).isEqualTo(v.ct() + v.tag());
      }
    }

    @Test
    void every_nist_encrypt_vector_decrypts_back_to_the_plaintext()
        throws GeneralSecurityException {
      for (Vector v : ENCRYPT) {
        byte[] data = HEX.parseHex(v.ct() + v.tag());
        byte[] pt =
            EnvelopeCodec.gcmDecrypt(
                null,
                new SecretKeySpec(HEX.parseHex(v.key()), "AES"),
                HEX.parseHex(v.iv()),
                HEX.parseHex(v.aad()),
                null,
                data);
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
        assertThatExceptionOfType(AEADBadTagException.class)
            .as(v.source())
            .isThrownBy(
                () ->
                    EnvelopeCodec.gcmDecrypt(
                        null,
                        new SecretKeySpec(HEX.parseHex(v.key()), "AES"),
                        HEX.parseHex(v.iv()),
                        HEX.parseHex(v.aad()),
                        null,
                        data));
      }
    }
  }

  @Nested
  class Aad_split_semantics {
    @Test
    void header_and_extra_aad_are_equivalent_to_their_concatenation()
        throws GeneralSecurityException {
      Vector v = ENCRYPT.get(2);
      byte[] aad = HEX.parseHex(v.aad());
      byte[] head = java.util.Arrays.copyOf(aad, 5);
      byte[] tail = java.util.Arrays.copyOfRange(aad, 5, aad.length);
      byte[] split =
          EnvelopeCodec.gcmEncrypt(
              null,
              new SecretKeySpec(HEX.parseHex(v.key()), "AES"),
              HEX.parseHex(v.iv()),
              head,
              tail,
              HEX.parseHex(v.pt()));
      assertThat(HEX.formatHex(split)).isEqualTo(v.ct() + v.tag());
    }
  }
}
