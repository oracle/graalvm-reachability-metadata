/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.security.Security;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseSecretKeyFactoryTest {
    private static final byte[] KEY_BYTES = new byte[8];

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void createsACustomKeySpecFromEncodedDesKeyMaterial() throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(
            "DES", BouncyCastleFipsProvider.PROVIDER_NAME);

        EncodedKeySpec keySpec = (EncodedKeySpec) factory.getKeySpec(
            new SecretKeySpec(KEY_BYTES, "DES"), EncodedKeySpec.class);

        assertThat(keySpec.getEncoded()).hasSize(8);
        assertThat(keySpec.getEncoded()).isNotEqualTo(KEY_BYTES);
    }

    public static final class EncodedKeySpec implements KeySpec {
        private final byte[] encoded;

        public EncodedKeySpec(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        public byte[] getEncoded() {
            return Arrays.copyOf(encoded, encoded.length);
        }
    }
}
