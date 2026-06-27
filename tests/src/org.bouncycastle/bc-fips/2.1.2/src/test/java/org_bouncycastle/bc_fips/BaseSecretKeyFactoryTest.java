/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.security.Provider;
import java.security.Security;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BaseSecretKeyFactoryTest {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void getKeySpecUsesPublicByteArrayKeySpecConstructor() throws Exception {
        Provider provider = bouncyCastleFipsProvider();
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("AES", provider);
        byte[] keyBytes = new byte[] {
            0x00, 0x01, 0x02, 0x03,
            0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b,
            0x0c, 0x0d, 0x0e, 0x0f,
            0x10, 0x11, 0x12, 0x13,
            0x14, 0x15, 0x16, 0x17
        };
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

        DESedeKeySpec keySpec = assertInstanceOf(
                DESedeKeySpec.class, keyFactory.getKeySpec(secretKey, DESedeKeySpec.class));

        assertArrayEquals(Arrays.copyOf(keyBytes, DESedeKeySpec.DES_EDE_KEY_LEN), keySpec.getKey());
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return new BouncyCastleFipsProvider();
    }
}
