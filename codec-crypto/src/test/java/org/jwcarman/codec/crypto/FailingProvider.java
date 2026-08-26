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

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.BadPaddingException;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/**
 * A JCA provider whose AES ciphers resolve and initialise normally but fail on every operation, so
 * the operational failure paths (wrap, encrypt) can be exercised deterministically.
 */
final class FailingProvider extends Provider {

  private static final long serialVersionUID = 1L;

  FailingProvider() {
    super("Failing", "1.0", "AES ciphers that fail on use");
    putService(
        new Service(this, "Cipher", "AES", FailingCipherSpi.class.getName(), null, null) {
          @Override
          public Object newInstance(Object constructorParameter) {
            return new FailingCipherSpi();
          }
        });
  }

  static final class FailingCipherSpi extends CipherSpi {

    @Override
    protected void engineSetMode(String mode) {}

    @Override
    protected void engineSetPadding(String padding) throws NoSuchPaddingException {}

    @Override
    protected int engineGetBlockSize() {
      return 16;
    }

    @Override
    protected int engineGetOutputSize(int inputLen) {
      return inputLen;
    }

    @Override
    protected byte[] engineGetIV() {
      return new byte[0];
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
      return null;
    }

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random)
        throws InvalidKeyException {}

    @Override
    protected void engineInit(
        int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {}

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {}

    @Override
    protected void engineUpdateAAD(byte[] src, int offset, int len) {}

    @Override
    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
      return new byte[0];
    }

    @Override
    protected int engineUpdate(
        byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) {
      return 0;
    }

    @Override
    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
        throws IllegalBlockSizeException, BadPaddingException {
      throw new BadPaddingException("FailingProvider refuses to encrypt");
    }

    @Override
    protected int engineDoFinal(
        byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
        throws IllegalBlockSizeException, BadPaddingException {
      throw new BadPaddingException("FailingProvider refuses to encrypt");
    }

    @Override
    protected byte[] engineWrap(Key key) throws IllegalBlockSizeException, InvalidKeyException {
      throw new InvalidKeyException("FailingProvider refuses to wrap");
    }

    @Override
    protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm, int wrappedKeyType)
        throws InvalidKeyException, NoSuchAlgorithmException {
      throw new InvalidKeyException("FailingProvider refuses to unwrap");
    }
  }
}
