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
package org.jwcarman.codec.versioned;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jwcarman.codec.spi.Codec;

/**
 * Prefixes every payload with a small self-describing header naming the codec that produced it, and
 * dispatches on that header when decoding — so an application can change its storage strategy
 * without rewriting what it has already stored.
 *
 * <p>The frame is three bytes: two magic bytes ({@code 0xC0 0xDC}), then an unsigned version in
 * {@code 1..255}, then the bytes the codec registered at that version produced.
 *
 * {@snippet lang = java :
 * Codec<Person> codec = VersionedCodec.<Person>builder()
 *         .version(1, jacksonFactory.create(Person.class))
 *         .version(2, foryFactory.create(Person.class).andThen(new ZstdCodec()))
 *         .writing(2)
 *         .build();
 * }
 *
 * <p>The write version is named explicitly rather than inferred from the highest registration,
 * because that is what makes a two-phase rollout possible: deploy with the new version registered
 * but still {@code .writing(1)}, let that reach every instance, then flip to {@code .writing(2)} in
 * a second deploy. No instance is ever handed data it cannot read.
 *
 * <p><strong>Ordering matters.</strong> Anything layered <em>outside</em> a versioned codec must be
 * undone before the header can be read, so it is frozen for the life of the store:
 *
 * {@snippet lang = java :
 * // header outermost: backend, transforms, everything inside may differ per version
 * Codec<Person> versioned = VersionedCodec.<Person>builder()
 *         .version(1, jackson.create(Person.class).andThen(new GzipCodec()))
 *         .version(2, fory.create(Person.class).andThen(new ZstdCodec()))
 *         .writing(2)
 *         .build();
 *
 * // header now inside gzip: gzip must be undone to read it, so gzip can never change
 * Codec<Person> frozen = versioned.andThen(new GzipCodec());
 * }
 *
 * <p>Put whatever you might want to change inside the versioned codec; put only permanent
 * commitments outside it.
 *
 * <p>Because the builder is generic over {@link Codec}, versioned byte transforms are simply the
 * {@code T = byte[]} case — a compression or key-wrapping layer that can be swapped under an
 * unchanged value codec.
 *
 * <p>Built codecs are immutable and thread-safe whenever their delegates are, which the {@link
 * Codec} contract already requires. Builders are not thread-safe.
 *
 * <p><strong>Failures.</strong> {@code decode} throws {@link VersionedFormatException} when the
 * buffer is shorter than the three-byte header or the magic does not match — bytes some other codec
 * wrote. Its subtype {@link UnknownVersionException} is thrown instead when the framing is valid
 * but names a version this codec has no registration for, and carries that version. Exceptions
 * thrown by a delegate codec propagate unchanged. {@code encode} throws {@link
 * NullPointerException} on a {@code null} value; wrap the built codec with {@link Codec#nullSafe()}
 * to opt into passing {@code null} straight through instead.
 */
public final class VersionedCodec {

  private static final byte MAGIC_0 = (byte) 0xC0;
  private static final byte MAGIC_1 = (byte) 0xDC;
  private static final int HEADER_LENGTH = 3;
  private static final int MIN_VERSION = 1;
  private static final int MAX_VERSION = 255;

  private VersionedCodec() {}

  /**
   * Starts building a versioned codec.
   *
   * @param <T> the type the codec converts
   * @return a new builder
   */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * Collects the per-version codecs and the version to write, and validates them.
   *
   * @param <T> the type the codec converts
   */
  public static final class Builder<T> {

    private final Map<Integer, Codec<T>> registrations = new HashMap<>();
    private int writeVersion = -1;

    private Builder() {}

    /**
     * Registers the codec that reads (and, if named by {@link #writing}, writes) a version.
     *
     * @param version the format version, 1-255; 0 is reserved
     * @param codec the codec that produced and can read that version's payloads
     * @return this builder
     * @throws IllegalArgumentException if {@code version} is outside 1-255
     * @throws IllegalStateException if {@code version} is already registered
     * @throws NullPointerException if {@code codec} is null
     */
    public Builder<T> version(int version, Codec<T> codec) {
      if (version < MIN_VERSION || version > MAX_VERSION) {
        throw new IllegalArgumentException(
            "version must be between " + MIN_VERSION + " and " + MAX_VERSION + ": " + version);
      }
      Objects.requireNonNull(codec, "codec must not be null");
      if (registrations.containsKey(version)) {
        throw new IllegalStateException("version already registered: " + version);
      }
      registrations.put(version, codec);
      return this;
    }

    /**
     * Names the version {@link Codec#encode} writes. Required. A later call replaces an earlier one
     * — there is exactly one write version, and the last call wins.
     *
     * @param version the format version to write, 1-255
     * @return this builder
     * @throws IllegalArgumentException if {@code version} is outside 1-255
     */
    public Builder<T> writing(int version) {
      if (version < MIN_VERSION || version > MAX_VERSION) {
        throw new IllegalArgumentException(
            "version must be between " + MIN_VERSION + " and " + MAX_VERSION + ": " + version);
      }
      writeVersion = version;
      return this;
    }

    /**
     * Builds the codec.
     *
     * @return an immutable versioned codec
     * @throws IllegalStateException if no version was registered, if {@link #writing} was never
     *     called, or if it names a version with no registered codec
     */
    public Codec<T> build() {
      if (registrations.isEmpty()) {
        throw new IllegalStateException("at least one version must be registered");
      }
      if (writeVersion < 0) {
        throw new IllegalStateException("a write version must be set with writing(int)");
      }
      if (!registrations.containsKey(writeVersion)) {
        throw new IllegalStateException("no codec registered for write version: " + writeVersion);
      }
      return new Dispatcher<>(Map.copyOf(registrations), writeVersion);
    }
  }

  private record Dispatcher<T>(Map<Integer, Codec<T>> codecs, int writeVersion)
      implements Codec<T> {

    @Override
    public byte[] encode(T value) {
      Objects.requireNonNull(value, "value must not be null");
      byte[] payload = codecs.get(writeVersion).encode(value);
      byte[] framed = new byte[HEADER_LENGTH + payload.length];
      framed[0] = MAGIC_0;
      framed[1] = MAGIC_1;
      framed[2] = (byte) writeVersion;
      System.arraycopy(payload, 0, framed, HEADER_LENGTH, payload.length);
      return framed;
    }

    @Override
    public T decode(byte[] bytes) {
      Objects.requireNonNull(bytes, "bytes must not be null");
      if (bytes.length < HEADER_LENGTH) {
        throw new VersionedFormatException(
            "not a versioned payload: expected at least "
                + HEADER_LENGTH
                + " bytes, got "
                + bytes.length);
      }
      if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) {
        throw new VersionedFormatException("not a versioned payload: bad magic");
      }
      int version = bytes[2] & 0xFF;
      Codec<T> codec = codecs.get(version);
      if (codec == null) {
        throw new UnknownVersionException(version);
      }
      return codec.decode(Arrays.copyOfRange(bytes, HEADER_LENGTH, bytes.length));
    }
  }
}
