/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;

import org.bouncycastle.jcajce.provider.symmetric.util.ClassUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class ClassUtilTest {
    private static final String AES_MAPPINGS_CLASS = "org.bouncycastle.jcajce.provider.symmetric.AES$Mappings";

    @Test
    void providerConstructionLoadsAlgorithmMappingsThroughClassUtil() throws Exception {
        BouncyCastleProvider provider = new BouncyCastleProvider();

        assertEquals(BouncyCastleProvider.PROVIDER_NAME, provider.getName());
        assertTrue(provider.hasAlgorithm("MessageDigest", "SHA-256"));
        assertTrue(provider.hasAlgorithm("Cipher", "AES"));
        assertEquals("BC", MessageDigest.getInstance("SHA-256", provider).getProvider().getName());
    }

    @Test
    void loadClassUsesLibraryClassLoaderForAlgorithmMappingClass() {
        Class<?> loadedClass = ClassUtil.loadClass(BouncyCastleProvider.class, AES_MAPPINGS_CLASS);

        assertNotNull(loadedClass);
        assertEquals(AES_MAPPINGS_CLASS, loadedClass.getName());
    }
}
