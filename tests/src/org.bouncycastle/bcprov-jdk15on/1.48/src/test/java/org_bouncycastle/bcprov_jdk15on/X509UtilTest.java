/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import java.security.Provider;
import java.security.Security;
import java.util.Collections;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.provider.X509CertParser;
import org.bouncycastle.jce.provider.X509StoreCertCollection;
import org.bouncycastle.x509.X509CollectionStoreParameters;
import org.bouncycastle.x509.X509Store;
import org.bouncycastle.x509.X509StreamParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class X509UtilTest {
    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;
    private static final String STREAM_PARSER_PROPERTY = "X509StreamParser.CUSTOM-CERTIFICATE";
    private static final String CERTIFICATE_PARSER_CLASS = "org.bouncycastle.jce.provider.X509CertParser";

    @Test
    void providerSpecificX509StoreLoadsImplementationWithProviderClassLoader() throws Exception {
        makeX509ImplementationsDiscoverableToNativeImage();
        BouncyCastleProvider provider = new BouncyCastleProvider();
        X509CollectionStoreParameters parameters = new X509CollectionStoreParameters(Collections.emptyList());

        X509Store store = X509Store.getInstance("CERTIFICATE/COLLECTION", parameters, provider);

        assertThat(provider.getName()).isEqualTo(PROVIDER_NAME);
        assertThat(store.getProvider()).isSameAs(provider);
        assertThat(store.getMatches(null)).isEmpty();
    }

    @Test
    void bootstrapProviderX509StreamParserLoadsImplementationWithClassForName() throws Exception {
        makeX509ImplementationsDiscoverableToNativeImage();
        Provider provider = Security.getProvider("SUN");
        assertThat(provider).isNotNull();
        Object previousMapping = provider.get(STREAM_PARSER_PROPERTY);
        assertThat(provider.getClass().getClassLoader()).isNull();

        try {
            provider.put(STREAM_PARSER_PROPERTY, CERTIFICATE_PARSER_CLASS);

            X509StreamParser parser = X509StreamParser.getInstance("CUSTOM-CERTIFICATE", provider);
            parser.init(new byte[0]);

            assertThat(parser.getProvider()).isSameAs(provider);
            assertThat(parser.read()).isNull();
        } finally {
            if (previousMapping == null) {
                provider.remove(STREAM_PARSER_PROPERTY);
            } else {
                provider.put(STREAM_PARSER_PROPERTY, previousMapping);
            }
        }
    }

    private static void makeX509ImplementationsDiscoverableToNativeImage() throws Exception {
        assertThat(Class.forName("org.bouncycastle.jce.provider.X509StoreCertCollection")
                .getDeclaredConstructor()
                .newInstance())
                .isInstanceOf(X509StoreCertCollection.class);
        assertThat(Class.forName("org.bouncycastle.jce.provider.X509CertParser")
                .getDeclaredConstructor()
                .newInstance())
                .isInstanceOf(X509CertParser.class);
    }
}
