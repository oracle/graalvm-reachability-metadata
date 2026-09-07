/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jcajce.provider.symmetric.util.BaseSecretKeyFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseSecretKeyFactoryTest {
    @Test
    void convertsSecretKeysToRequestedKeySpecs() throws Exception {
        ExposedKeyFactory factory = new ExposedKeyFactory();
        SecretKey original = new SecretKeySpec(new byte[16], "AES");

        EncodedKeySpec specification = (EncodedKeySpec) factory.getKeySpec(original, EncodedKeySpec.class);

        assertThat(specification.getEncoded()).containsExactly(original.getEncoded());
    }

    public static class ExposedKeyFactory extends BaseSecretKeyFactory {
        public ExposedKeyFactory() {
            super("AES", null);
        }

        public KeySpec getKeySpec(SecretKey key, Class<?> specificationType) throws Exception {
            return engineGetKeySpec(key, specificationType);
        }
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
