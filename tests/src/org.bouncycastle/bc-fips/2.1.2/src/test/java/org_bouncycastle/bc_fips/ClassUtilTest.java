/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.Provider;
import java.security.Security;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.Test;

public class ClassUtilTest {
    private static final byte[] AES_KEY = new byte[] {
        0x00, 0x01, 0x02, 0x03,
        0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0a, 0x0b,
        0x0c, 0x0d, 0x0e, 0x0f
    };
    private static final byte[] GCM_NONCE = new byte[] {
        0x10, 0x11, 0x12, 0x13,
        0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1a, 0x1b
    };

    private static Provider newProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return TestProviders.bcFipsProvider();
    }

    private static byte[] encrypt(Provider provider, SecretKeySpec key, byte[] nonce, byte[] plaintext) throws Exception {
        Cipher encrypt = newGcmCipher(provider, Cipher.ENCRYPT_MODE, key, nonce);
        return encrypt.doFinal(plaintext);
    }

    private static Cipher newGcmCipher(Provider provider, int mode, SecretKeySpec key, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        return cipher;
    }

    private static byte[] nonceWithLastByte(int lastByte) {
        byte[] nonce = GCM_NONCE.clone();
        nonce[nonce.length - 1] = (byte)lastByte;
        return nonce;
    }
}
