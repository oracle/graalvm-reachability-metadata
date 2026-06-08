/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.symmetric.AES;
import org.bouncycastle.jcajce.spec.AEADParameterSpec;
import org.junit.jupiter.api.Test;

public class BaseBlockCipherInnerAEADGenericBlockCipherTest {
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
    private static final byte[] AAD_BYTES = new byte[] {
        0x20, 0x21, 0x22, 0x23,
        0x24, 0x25, 0x26, 0x27
    };
    private static final byte[] PLAINTEXT_BYTES = new byte[] {
        0x30, 0x31, 0x32, 0x33,
        0x34, 0x35, 0x36, 0x37,
        0x38, 0x39, 0x3a, 0x3b,
        0x3c, 0x3d, 0x3e, 0x3f
    };

    @Test
    void gcmBadAuthenticationTagIsReportedAsPaddingFailure() throws Exception {
        byte[] ciphertextWithTag = encrypt();
        ciphertextWithTag[ciphertextWithTag.length - 1] ^= 0x01;

        ExposedGcmCipher decryptCipher = new ExposedGcmCipher();
        decryptCipher.init(Cipher.DECRYPT_MODE, keySpec(), aeadSpec());

        BadPaddingException exception = assertThrows(
            BadPaddingException.class, () -> decryptCipher.doFinal(ciphertextWithTag));

        boolean isAeadFailure = exception instanceof AEADBadTagException;
        boolean isFallbackFailure = exception.getClass() == BadPaddingException.class;
        assertTrue(isAeadFailure || isFallbackFailure);
    }

    @Test
    void gcmRoundTripUsesBaseBlockAeadCipher() throws Exception {
        byte[] ciphertextWithTag = encrypt();

        ExposedGcmCipher decryptCipher = new ExposedGcmCipher();
        decryptCipher.init(Cipher.DECRYPT_MODE, keySpec(), aeadSpec());

        assertArrayEquals(PLAINTEXT_BYTES, decryptCipher.doFinal(ciphertextWithTag));
    }

    private static byte[] encrypt() throws Exception {
        ExposedGcmCipher encryptCipher = new ExposedGcmCipher();
        encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec(), aeadSpec());
        return encryptCipher.doFinal(PLAINTEXT_BYTES);
    }

    private static SecretKeySpec keySpec() {
        return new SecretKeySpec(KEY_BYTES, "AES");
    }

    private static AEADParameterSpec aeadSpec() {
        return new AEADParameterSpec(IV_BYTES, 128, AAD_BYTES);
    }

    private static final class ExposedGcmCipher extends AES.GCM {
        void init(int opmode, Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
            engineInit(opmode, key, params, null);
        }

        byte[] doFinal(byte[] input) throws IllegalBlockSizeException, BadPaddingException {
            return engineDoFinal(input, 0, input.length);
        }
    }
}
