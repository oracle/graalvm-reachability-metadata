/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.Cipher;
import com.jcraft.jsch.HASH;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Random;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class KeyPairTest {
    private static final byte[] PASSPHRASE =
            "correct horse battery staple".getBytes(StandardCharsets.UTF_8);

    @Test
    void instantiatesConfiguredCipherHashAndRandomForEncryptedPrivateKey() throws JSchException {
        resetInstrumentedProviders();
        configureInstrumentedProviders();
        KeyPair keyPair = null;
        try {
            keyPair = generateRsaKeyPair();
            assertThat(keyPair.getFingerPrint()).isNotBlank();
            keyPair.setPassphrase(PASSPHRASE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            keyPair.writePrivateKey(output);

            assertThat(new String(output.toByteArray(), StandardCharsets.US_ASCII))
                    .contains("BEGIN RSA PRIVATE KEY")
                    .contains("Proc-Type: 4,ENCRYPTED")
                    .contains("DEK-Info: DES-EDE3-CBC");
            assertThat(InstrumentedCipher.constructorCalls.get()).isEqualTo(1);
            assertThat(InstrumentedHash.constructorCalls.get()).isEqualTo(1);
            assertThat(InstrumentedRandom.constructorCalls.get()).isEqualTo(1);
            assertThat(InstrumentedCipher.updateCalls.get()).isPositive();
            assertThat(InstrumentedHash.digestCalls.get()).isPositive();
            assertThat(InstrumentedRandom.fillCalls.get()).isPositive();
        } finally {
            if (keyPair != null) {
                keyPair.dispose();
            }
            restoreDefaultProviders();
        }
    }

    @Test
    void calculatesFingerprintWithConfiguredHash() throws JSchException {
        KeyPair keyPair = generateRsaKeyPair();
        try {
            assertThat(keyPair.getFingerPrint())
                    .startsWith("512 ")
                    .contains(":");
        } finally {
            keyPair.dispose();
        }
    }

    @Test
    void writesEncryptedPrivateKeyWithConfiguredCipherHashAndRandom() throws JSchException {
        KeyPair keyPair = generateRsaKeyPair();
        try {
            keyPair.setPassphrase(PASSPHRASE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            keyPair.writePrivateKey(output);

            assertThat(new String(output.toByteArray(), StandardCharsets.US_ASCII))
                    .contains("BEGIN RSA PRIVATE KEY")
                    .contains("Proc-Type: 4,ENCRYPTED")
                    .contains("DEK-Info: DES-EDE3-CBC");
        } finally {
            keyPair.dispose();
        }
    }

    private static KeyPair generateRsaKeyPair() throws JSchException {
        return KeyPair.genKeyPair(new JSch(), KeyPair.RSA, 512);
    }

    private static void configureInstrumentedProviders() {
        Hashtable<String, String> config = new Hashtable<>();
        config.put("3des-cbc", InstrumentedCipher.class.getName());
        config.put("md5", InstrumentedHash.class.getName());
        config.put("random", InstrumentedRandom.class.getName());
        JSch.setConfig(config);
    }

    private static void restoreDefaultProviders() {
        Hashtable<String, String> config = new Hashtable<>();
        config.put("3des-cbc", "com.jcraft.jsch.jce.TripleDESCBC");
        config.put("md5", "com.jcraft.jsch.jce.MD5");
        config.put("random", "com.jcraft.jsch.jce.Random");
        JSch.setConfig(config);
    }

    private static void resetInstrumentedProviders() {
        InstrumentedCipher.constructorCalls.set(0);
        InstrumentedCipher.updateCalls.set(0);
        InstrumentedHash.constructorCalls.set(0);
        InstrumentedHash.digestCalls.set(0);
        InstrumentedRandom.constructorCalls.set(0);
        InstrumentedRandom.fillCalls.set(0);
    }

    public static final class InstrumentedCipher implements Cipher {
        static final AtomicInteger constructorCalls = new AtomicInteger();
        static final AtomicInteger updateCalls = new AtomicInteger();

        public InstrumentedCipher() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public int getIVSize() {
            return 8;
        }

        @Override
        public int getBlockSize() {
            return 24;
        }

        @Override
        public void init(int mode, byte[] key, byte[] iv) {
        }

        @Override
        public void update(byte[] source, int sourceOffset, int length, byte[] target, int targetOffset) {
            updateCalls.incrementAndGet();
            System.arraycopy(source, sourceOffset, target, targetOffset, length);
        }
    }

    public static final class InstrumentedHash implements HASH {
        static final AtomicInteger constructorCalls = new AtomicInteger();
        static final AtomicInteger digestCalls = new AtomicInteger();
        private int updatedBytes;

        public InstrumentedHash() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public void init() {
        }

        @Override
        public int getBlockSize() {
            return 16;
        }

        @Override
        public void update(byte[] value, int start, int length) {
            updatedBytes += length;
        }

        @Override
        public byte[] digest() {
            digestCalls.incrementAndGet();
            byte[] digest = new byte[getBlockSize()];
            digest[0] = (byte) updatedBytes;
            return digest;
        }
    }

    public static final class InstrumentedRandom implements Random {
        static final AtomicInteger constructorCalls = new AtomicInteger();
        static final AtomicInteger fillCalls = new AtomicInteger();

        public InstrumentedRandom() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public void fill(byte[] value, int start, int length) {
            fillCalls.incrementAndGet();
            for (int index = start; index < start + length; index++) {
                value[index] = (byte) index;
            }
        }
    }
}
