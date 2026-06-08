/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.security.Provider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class GcmSpecUtilAnonymous3Test {
    private static final byte[] KEY_BYTES = new byte[] {
        0x00, 0x01, 0x02, 0x03,
        0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0a, 0x0b,
        0x0c, 0x0d, 0x0e, 0x0f
    };
    private static final byte[] IV_BYTES = new byte[] {
        0x10, 0x11, 0x12, 0x13,
        0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1a, 0x1b
    };
    private static final byte[] PLAINTEXT_BYTES = new byte[] {
        0x30, 0x31, 0x32, 0x33,
        0x34, 0x35, 0x36, 0x37,
        0x38, 0x39, 0x3a, 0x3b,
        0x3c, 0x3d, 0x3e, 0x3f
    };

    @Test
    void aesGcmCipherAcceptsJdkGcmParameterSpec() throws Exception {
        assertGcmRoundTrip("AES/GCM/NoPadding");
    }

    @Test
    void directGcmCipherAcceptsJdkGcmParameterSpec() throws Exception {
        assertGcmRoundTrip("GCM");
    }

    private static void assertGcmRoundTrip(String transformation) throws Exception {
        Provider provider = provider();
        Cipher encryptCipher = Cipher.getInstance(transformation, provider);
        encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec(), gcmSpec());
        byte[] ciphertextWithTag = encryptCipher.doFinal(PLAINTEXT_BYTES);

        Cipher decryptCipher = Cipher.getInstance(transformation, provider);
        decryptCipher.init(Cipher.DECRYPT_MODE, keySpec(), gcmSpec());

        assertArrayEquals(PLAINTEXT_BYTES, decryptCipher.doFinal(ciphertextWithTag));
    }

    private static Provider provider() {
        return new BouncyCastleProvider();
    }

    private static SecretKeySpec keySpec() {
        return new SecretKeySpec(KEY_BYTES, "AES");
    }

    private static GCMParameterSpec gcmSpec() {
        return new GCMParameterSpec(128, IV_BYTES);
    }
}
