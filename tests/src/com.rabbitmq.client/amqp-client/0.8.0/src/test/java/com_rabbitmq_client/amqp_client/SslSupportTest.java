/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import com.rabbitmq.qpid.protonj2.client.SslOptions;
import com.rabbitmq.qpid.protonj2.client.transport.netty4.SslSupport;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SslSupportTest {

    private static final String TRUST_STORE_RESOURCE = "ssl-support-test-truststore.p12";
    private static final String TRUST_STORE_PASSWORD = "changeit";

    @Test
    void jdkSslContextLoadsTrustStoreFromClasspathLocation(@TempDir Path directory) throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Path trustStore = directory.resolve(TRUST_STORE_RESOURCE);
        Files.write(trustStore, createEmptyPkcs12Store());
        Thread.currentThread().setContextClassLoader(
                new TrustStoreResourceClassLoader(originalContextClassLoader, trustStore.toUri().toURL()));
        try {
            SslOptions options = new SslOptions()
                    .keyStoreLocation(null)
                    .trustStoreLocation("classpath:" + TRUST_STORE_RESOURCE)
                    .trustStorePassword(TRUST_STORE_PASSWORD)
                    .trustStoreType("PKCS12")
                    .allowNativeSSL(false);

            SSLContext context = SslSupport.createJdkSslContext(options);

            assertThat(context).isNotNull();
            assertThat(context.getProtocol()).isEqualTo(options.contextProtocol());
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static byte[] createEmptyPkcs12Store() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, TRUST_STORE_PASSWORD.toCharArray());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        keyStore.store(output, TRUST_STORE_PASSWORD.toCharArray());
        return output.toByteArray();
    }

    private static final class TrustStoreResourceClassLoader extends ClassLoader {

        private final URL trustStore;

        private TrustStoreResourceClassLoader(ClassLoader parent, URL trustStore) {
            super(parent);
            this.trustStore = trustStore;
        }

        @Override
        protected URL findResource(String name) {
            if (TRUST_STORE_RESOURCE.equals(name)) {
                return trustStore;
            }
            return null;
        }
    }
}
