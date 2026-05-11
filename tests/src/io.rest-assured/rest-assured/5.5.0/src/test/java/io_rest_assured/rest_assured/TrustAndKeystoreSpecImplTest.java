/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.TrustAndKeystoreSpec;
import io.restassured.internal.TrustAndKeystoreSpecImpl;
import io.restassured.internal.TrustAndKeystoreSpecImplDirectAccess;
import org.junit.jupiter.api.Test;

import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TrustAndKeystoreSpecImplTest {
    private static final String DYNAMIC_RESOLUTION_TARGET = "io.restassured.internal.TrustAndKeystoreSpec";

    @Test
    void generatedAccessClassReachesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = TrustAndKeystoreSpecImplDirectAccess
                .resolveWithCompilerGeneratedClassResolver(DYNAMIC_RESOLUTION_TARGET);

        assertSame(TrustAndKeystoreSpec.class, resolvedClass);
    }

    @Test
    void emptyStorePathDoesNotLoadKeyStore() {
        TrustAndKeystoreSpecImpl specification = new TrustAndKeystoreSpecImpl();

        KeyStore keyStore = specification.createStore(KeyStore.getDefaultType(), "", "changeit");

        assertNull(keyStore);
    }

    @Test
    void exposesConfiguredHttpsPort() {
        TrustAndKeystoreSpecImpl specification = new TrustAndKeystoreSpecImpl();

        specification.setPort(8443);

        assertEquals(8443, specification.getPort());
    }
}
