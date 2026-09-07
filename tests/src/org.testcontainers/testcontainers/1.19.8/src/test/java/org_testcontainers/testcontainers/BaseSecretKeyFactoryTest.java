/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.Provider;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseSecretKeyFactoryTest {
    @Test
    void convertsSecretKeysToRequestedKeySpecs() throws Exception {
        Provider provider = new BouncyCastleProvider();
        SecretKeyFactory factory = SecretKeyFactory.getInstance("AES", provider);
        SecretKey original = new SecretKeySpec(new byte[16], "AES");

        EncodedKeySpec specification = (EncodedKeySpec) factory.getKeySpec(original, EncodedKeySpec.class);

        assertThat(specification.getEncoded()).containsExactly(original.getEncoded());
    }

    public static class EncodedKeySpec implements KeySpec {
        private final byte[] encoded;

        public EncodedKeySpec(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        public byte[] getEncoded() {
            return encoded.clone();
        }
    }
}
