/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import java.security.Provider;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.SecretKeyFactorySpi;
import javax.crypto.spec.DESKeySpec;

import org.bouncycastle.crypto.params.DESParameters;
import org.bouncycastle.jcajce.provider.symmetric.util.BaseSecretKeyFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BaseSecretKeyFactoryTest {
    private static final byte[] SECRET_KEY_BYTES = new byte[] {
        0x01, 0x23, 0x45, 0x67, (byte)0x89, (byte)0xab, (byte)0xcd, (byte)0xef
    };

    @Test
    void secretKeyFactoryCreatesRequestedByteArrayKeySpec() throws Exception {
        SecretKeyFactory factory = new TestSecretKeyFactory(new BaseSecretKeyFactory.DES());
        SecretKey generatedKey = factory.generateSecret(new DESKeySpec(SECRET_KEY_BYTES));

        DESKeySpec desKeySpec = (DESKeySpec)factory.getKeySpec(generatedKey, DESKeySpec.class);

        assertThat(desKeySpec.getKey()).isEqualTo(Arrays.copyOf(generatedKey.getEncoded(), DESKeySpec.DES_KEY_LEN));
    }

    @Test
    void secretKeyFactoryRejectsNonKeySpecClassAfterConstructingItFromEncodedKey() throws Exception {
        SecretKeyFactory factory = new TestSecretKeyFactory(new BaseSecretKeyFactory.DES());
        SecretKey generatedKey = factory.generateSecret(new DESKeySpec(SECRET_KEY_BYTES));

        assertThatThrownBy(() -> factory.getKeySpec(generatedKey, DESParameters.class))
            .isInstanceOf(InvalidKeySpecException.class);
    }

    public static class TestSecretKeyFactory extends SecretKeyFactory {
        public TestSecretKeyFactory(SecretKeyFactorySpi keyFactorySpi) {
            super(keyFactorySpi, new TestProvider(), "DES");
        }
    }

    public static class TestProvider extends Provider {
        private static final long serialVersionUID = 1L;

        TestProvider() {
            super("TEST", 1.0, "Test provider for BaseSecretKeyFactory");
        }
    }
}
