/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.security.Security;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseSecretKeyFactoryTest {
    private static final byte[] KEY = new byte[] {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void convertsAnAesSecretKeyToACustomByteArrayKeySpec() throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(
            "AES", BouncyCastleFipsProvider.PROVIDER_NAME);
        SecretKey key = factory.generateSecret(new SecretKeySpec(KEY, "AES"));

        ByteArrayKeySpec keySpec = (ByteArrayKeySpec)factory.getKeySpec(key, ByteArrayKeySpec.class);

        assertThat(keySpec.getEncoded()).isEqualTo(KEY);
    }

    public static final class ByteArrayKeySpec implements KeySpec {
        private final byte[] encoded;

        public ByteArrayKeySpec(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        public byte[] getEncoded() {
            return encoded.clone();
        }
    }
}
