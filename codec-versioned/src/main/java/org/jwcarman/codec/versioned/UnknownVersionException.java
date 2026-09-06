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

/**
 * Signals that a payload carries valid versioned framing but names a version this codec has no
 * codec registered for.
 *
 * <p>During a rollout this is the "written by a newer deploy" signal — a condition a caller may
 * choose to route or retry rather than treat as corruption — which is why it is distinguishable
 * from its {@link VersionedFormatException} parent.
 */
public class UnknownVersionException extends VersionedFormatException {

  private final int version;

  /**
   * Creates an exception naming the version that could not be dispatched.
   *
   * @param version the unrecognized format version, 0-255
   */
  public UnknownVersionException(int version) {
    super("unknown format version: " + version);
    this.version = version;
  }

  /**
   * Returns the unrecognized format version read from the payload.
   *
   * @return the version, 0-255
   */
  public int version() {
    return version;
  }
}
