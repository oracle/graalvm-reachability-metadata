/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.Provider;
import java.security.Security;
import java.util.Collection;
import java.util.Collections;

import org.bouncycastle.util.Selector;
import org.bouncycastle.x509.X509Store;
import org.bouncycastle.x509.X509StoreParameters;
import org.bouncycastle.x509.X509StoreSpi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class X509UtilTest {
    private static final String LOAD_CLASS_ALGORITHM = "LOADCLASSX509UTILTEST";
    private static final String CLASS_FOR_NAME_ALGORITHM = "CLASSFORNAMEX509UTILTEST";

    @Test
    void x509StoreInstantiatesImplementationThroughProviderClassLoader() throws Exception {
        TestProvider provider = new TestProvider(LOAD_CLASS_ALGORITHM, RecordingX509StoreSpi.class);

        X509Store store = X509Store.getInstance(LOAD_CLASS_ALGORITHM, NoOpX509StoreParameters.INSTANCE, provider);

        assertThat(store.getProvider()).isSameAs(provider);
        assertThat(store.getMatches(null)).isEmpty();
    }

    @Test
    void x509StoreInstantiatesImplementationThroughClassForNameForBootstrapProvider() throws Exception {
        Provider provider = findBootstrapProvider();
        String serviceKey = "X509Store." + CLASS_FOR_NAME_ALGORITHM;
        Object previousValue = provider.put(serviceKey, RecordingX509StoreSpi.class.getName());
        try {
            X509Store store = X509Store.getInstance(
                    CLASS_FOR_NAME_ALGORITHM,
                    NoOpX509StoreParameters.INSTANCE,
                    provider);

            assertThat(store.getProvider()).isSameAs(provider);
            assertThat(store.getMatches(null)).isEmpty();
        } finally {
            if (previousValue == null) {
                provider.remove(serviceKey);
            } else {
                provider.put(serviceKey, previousValue);
            }
        }
    }

    private static Provider findBootstrapProvider() {
        for (Provider provider : Security.getProviders()) {
            if (provider.getClass().getClassLoader() == null) {
                return provider;
            }
        }
        throw new IllegalStateException("No bootstrap-loaded security provider found");
    }

    public static final class TestProvider extends Provider {
        public TestProvider(String algorithm, Class<?> implementationClass) {
            super("X509UtilTestProvider-" + algorithm, "1.0", "X509Util test provider");
            put("X509Store." + algorithm, implementationClass.getName());
        }
    }

    public static final class RecordingX509StoreSpi extends X509StoreSpi {
        @Override
        public void engineInit(X509StoreParameters parameters) {
            assertThat(parameters).isSameAs(NoOpX509StoreParameters.INSTANCE);
        }

        @Override
        public Collection engineGetMatches(Selector selector) {
            return Collections.emptyList();
        }
    }

    private enum NoOpX509StoreParameters implements X509StoreParameters {
        INSTANCE
    }
}
