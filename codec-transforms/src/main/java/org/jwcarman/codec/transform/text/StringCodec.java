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
package org.jwcarman.codec.transform.text;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jwcarman.codec.spi.Codec;

/**
 * A {@code Codec<String>} whose bytes are simply the text in a charset — no quoting, no framing.
 * This is what a backend's {@code create(String.class)} does <em>not</em> give you: a JSON backend
 * encodes {@code hello} as the seven bytes {@code "hello"}. Use this for payloads that already are
 * text, and as a backend-free base for {@link Codec#xmap}:
 *
 * {@snippet lang = java :
 * Codec<UUID> ids = StringCodec.utf8().xmap(UUID::fromString, UUID::toString);
 * }
 *
 * <p>Decoding is strict: bytes that are not valid in the charset are rejected with {@link
 * IllegalArgumentException} rather than silently replaced with U+FFFD, so corruption surfaces as an
 * error instead of a wrong answer. Encoding uses the JDK's default replacement for characters the
 * charset cannot represent (an unpaired surrogate in UTF-8, a non-Latin character in ISO-8859-1
 * becomes {@code ?}).
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class StringCodec implements Codec<String> {

  private final Charset charset;

  private StringCodec(Charset charset) {
    this.charset = charset;
  }

  /**
   * UTF-8, the right choice unless an external system dictates otherwise.
   *
   * @return a UTF-8 string codec
   */
  public static StringCodec utf8() {
    return new StringCodec(StandardCharsets.UTF_8);
  }

  /**
   * Any charset.
   *
   * @param charset the charset to encode with and decode strictly against
   * @return a string codec for the charset
   * @throws NullPointerException if {@code charset} is null
   */
  public static StringCodec of(Charset charset) {
    return new StringCodec(Objects.requireNonNull(charset, "charset must not be null"));
  }

  @Override
  public byte[] encode(String value) {
    Objects.requireNonNull(value, "value must not be null");
    return value.getBytes(charset);
  }

  @Override
  public String decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    try {
      return charset
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException("Input is not valid " + charset.name(), e);
    }
  }
}
