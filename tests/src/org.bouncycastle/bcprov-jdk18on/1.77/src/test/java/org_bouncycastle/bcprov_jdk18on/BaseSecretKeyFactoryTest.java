/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class BaseSecretKeyFactoryTest {
    @Test
    void exportsAesKeyToApplicationSpecification() throws Exception {
        byte[] material = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        SecretKey key = new SecretKeySpec(material, "AES");
        SecretKeyFactory factory = SecretKeyFactory.getInstance("AES", KeyPairFixtures.BC);
        RawKeySpec spec = (RawKeySpec) factory.getKeySpec(key, RawKeySpec.class);
        assertArrayEquals(material, spec.getEncoded());
    }

    public static class RawKeySpec implements KeySpec {
        private final byte[] encoding;

        public RawKeySpec(byte[] encoding) {
            this.encoding = encoding.clone();
        }

        public byte[] getEncoded() {
            return encoding.clone();
        }
    }
}
