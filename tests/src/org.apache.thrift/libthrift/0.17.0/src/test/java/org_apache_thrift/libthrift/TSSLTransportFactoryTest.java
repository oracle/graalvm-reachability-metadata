/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_thrift.libthrift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TServerSocket;
import org.junit.jupiter.api.Test;

public class TSSLTransportFactoryTest {
    private static final String TRUST_STORE_RESOURCE = "org_apache_thrift/libthrift/test-truststore.jks";
    private static final String TRUST_STORE_PASSWORD = "changeit";

    @Test
    void sslContextLoadsTrustStoreFromContextClassLoaderResource() throws Exception {
        byte[] trustStore = createEmptyTrustStore();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ResourceClassLoader resourceClassLoader = new ResourceClassLoader(originalClassLoader, trustStore);
        TServerSocket serverSocket = null;

        try {
            Thread.currentThread().setContextClassLoader(resourceClassLoader);

            TSSLTransportFactory.TSSLTransportParameters parameters =
                    new TSSLTransportFactory.TSSLTransportParameters();
            parameters.setTrustStore(TRUST_STORE_RESOURCE, TRUST_STORE_PASSWORD);

            serverSocket = TSSLTransportFactory.getServerSocket(0, 0, null, parameters);

            assertThat(serverSocket.getServerSocket().isBound()).isTrue();
            assertThat(resourceClassLoader.resourceWasRequested()).isTrue();
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static byte[] createEmptyTrustStore() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, TRUST_STORE_PASSWORD.toCharArray());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        keyStore.store(output, TRUST_STORE_PASSWORD.toCharArray());
        return output.toByteArray();
    }

    private static class ResourceClassLoader extends ClassLoader {
        private final byte[] trustStore;
        private final AtomicBoolean requested = new AtomicBoolean();

        ResourceClassLoader(ClassLoader parent, byte[] trustStore) {
            super(parent);
            this.trustStore = trustStore.clone();
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (TRUST_STORE_RESOURCE.equals(name)) {
                requested.set(true);
                return new ByteArrayInputStream(trustStore);
            }
            return super.getResourceAsStream(name);
        }

        boolean resourceWasRequested() {
            return requested.get();
        }
    }
}
