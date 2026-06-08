/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import java.security.Provider;
import java.security.Security;
import java.util.Collections;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509CollectionStoreParameters;
import org.bouncycastle.x509.X509Store;
import org.junit.jupiter.api.Test;

public class X509UtilTest {
    private static final String TEST_STORE_TYPE = "BCPROVTESTCERTIFICATE/COLLECTION";
    private static final String TEST_STORE_PROPERTY = "X509Store." + TEST_STORE_TYPE;
    private static final String X509_STORE_CERT_COLLECTION_CLASS =
        "org.bouncycastle.jce.provider.X509StoreCertCollection";

    @Test
    @SuppressWarnings("deprecation")
    void x509StoreUsesExplicitProviderClassLoaderImplementationLookup() throws Exception {
        Provider provider = new BouncyCastleProvider();
        X509CollectionStoreParameters parameters = emptyCollectionParameters();

        X509Store store = X509Store.getInstance("CERTIFICATE/COLLECTION", parameters, provider);

        assertSame(provider, store.getProvider());
        assertEquals(Collections.emptyList(), store.getMatches(null));
    }

    @Test
    @SuppressWarnings("deprecation")
    void x509StoreUsesInstalledProviderClassLoaderImplementationLookup() throws Exception {
        Provider previousProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        Provider provider = new BouncyCastleProvider();

        if (previousProvider != null) {
            Security.removeProvider(previousProvider.getName());
        }
        Security.insertProviderAt(provider, 1);

        try {
            X509Store store = X509Store.getInstance(
                "CERTIFICATE/COLLECTION", emptyCollectionParameters());

            assertSame(provider, store.getProvider());
            assertEquals(Collections.emptyList(), store.getMatches(null));
        } finally {
            Security.removeProvider(provider.getName());
            if (previousProvider != null) {
                Security.addProvider(previousProvider);
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void x509StoreUsesBootstrapProviderImplementationLookup() throws Exception {
        Provider provider = bootstrapLoadedProvider();
        Object previousValue = provider.put(TEST_STORE_PROPERTY, X509_STORE_CERT_COLLECTION_CLASS);

        try {
            X509Store store = X509Store.getInstance(
                TEST_STORE_TYPE, emptyCollectionParameters(), provider);

            assertSame(provider, store.getProvider());
            assertEquals(Collections.emptyList(), store.getMatches(null));
        } finally {
            restoreProviderProperty(provider, previousValue);
        }
    }

    private static X509CollectionStoreParameters emptyCollectionParameters() {
        return new X509CollectionStoreParameters(Collections.emptyList());
    }

    private static Provider bootstrapLoadedProvider() {
        for (Provider provider : Security.getProviders()) {
            if (provider.getClass().getClassLoader() == null) {
                return provider;
            }
        }
        return fail("Expected at least one bootstrap-loaded JCA provider");
    }

    private static void restoreProviderProperty(Provider provider, Object previousValue) {
        if (previousValue == null) {
            provider.remove(TEST_STORE_PROPERTY);
        } else {
            provider.put(TEST_STORE_PROPERTY, previousValue);
        }
    }
}
