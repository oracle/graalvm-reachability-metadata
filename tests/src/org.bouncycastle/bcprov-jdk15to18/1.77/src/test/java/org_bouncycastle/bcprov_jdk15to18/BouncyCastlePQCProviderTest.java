/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Provider;
import java.security.Security;

import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.XMSS;
import org.junit.jupiter.api.Test;

public class BouncyCastlePQCProviderTest {
    private static final Class<?> REQUIRED_MAPPING_CLASS = XMSS.Mappings.class;

    @Test
    void constructorInitializesProviderThroughPublicApi() {
        BouncyCastlePQCProvider provider = new BouncyCastlePQCProvider();
        Provider previousProvider = Security.getProvider(provider.getName());
        if (previousProvider != null) {
            Security.removeProvider(previousProvider.getName());
        }
        Security.addProvider(provider);

        try {
            assertEquals(BouncyCastlePQCProvider.PROVIDER_NAME, provider.getName());
            assertEquals("org.bouncycastle.pqc.jcajce.provider.XMSS$Mappings", REQUIRED_MAPPING_CLASS.getName());
            assertNotNull(provider.getVersionStr());
            assertNotNull(provider.getInfo());
            assertTrue(provider.hasAlgorithm("KeyFactory", "XMSS"));
            assertTrue(provider.hasAlgorithm("Signature", "XMSS"));
            assertSame(provider, Security.getProvider(provider.getName()));
        } finally {
            Security.removeProvider(provider.getName());
            if (previousProvider != null) {
                Security.addProvider(previousProvider);
            }
        }
    }
}
