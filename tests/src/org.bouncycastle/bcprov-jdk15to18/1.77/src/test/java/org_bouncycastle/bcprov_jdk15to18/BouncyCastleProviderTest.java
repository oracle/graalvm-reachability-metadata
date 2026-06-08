/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;

import javax.crypto.Cipher;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class BouncyCastleProviderTest {
    @Test
    void constructorRegistersAlgorithmsForJcaLookups() throws Exception {
        BouncyCastleProvider provider = new BouncyCastleProvider();
        Provider previousProvider = Security.getProvider(provider.getName());
        if (previousProvider != null) {
            Security.removeProvider(previousProvider.getName());
        }
        Security.addProvider(provider);

        try {
            assertEquals(BouncyCastleProvider.PROVIDER_NAME, provider.getName());
            assertNotNull(provider.getVersionStr());
            assertNotNull(provider.getInfo());
            assertTrue(provider.hasAlgorithm("MessageDigest", "SHA-256"));
            assertTrue(provider.hasAlgorithm("Cipher", "AES"));
            assertTrue(provider.hasAlgorithm("KeyFactory", "RSA"));
            assertSame(provider, Security.getProvider(provider.getName()));

            Provider.Service digestService = provider.getService("MessageDigest", "SHA-256");
            assertNotNull(digestService);
            assertInstanceOf(MessageDigest.class, digestService.newInstance(null));

            MessageDigest digest = MessageDigest.getInstance("SHA-256", BouncyCastleProvider.PROVIDER_NAME);
            byte[] hash = digest.digest("bc-provider".getBytes(StandardCharsets.UTF_8));
            assertEquals(32, hash.length);
            assertSame(provider, digest.getProvider());

            Provider.Service cipherService = provider.getService("Cipher", "AES");
            assertNotNull(cipherService);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding", BouncyCastleProvider.PROVIDER_NAME);
            assertSame(provider, cipher.getProvider());

            Provider.Service keyFactoryService = provider.getService("KeyFactory", "RSA");
            assertNotNull(keyFactoryService);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            assertSame(provider, keyFactory.getProvider());
        } finally {
            Security.removeProvider(provider.getName());
            if (previousProvider != null) {
                Security.addProvider(previousProvider);
            }
        }
    }
}
