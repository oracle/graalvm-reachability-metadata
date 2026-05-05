/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.junit.jupiter.api.Test;
import org.keycloak.common.constants.GenericConstants;
import org.keycloak.common.util.KeystoreUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;

public class KeystoreUtilTest {
    private static final String PASSWORD = "changeit";

    @Test
    void loadsClasspathKeyStoreFromContextClassLoader() throws Exception {
        String resourceName = "context-classloader-keystore.p12";
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(new InMemoryResourceClassLoader(
                    resourceName,
                    createEmptyKeyStore()));

            KeyStore keyStore = KeystoreUtil.loadKeyStore(
                    GenericConstants.PROTOCOL_CLASSPATH + resourceName,
                    PASSWORD,
                    KeystoreUtil.KeystoreFormat.PKCS12.toString());

            assertThat(keyStore.size()).isZero();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void loadsClasspathKeyStoreFromClassResourceWhenContextClassLoaderMisses() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(new MissingResourceClassLoader());

            KeyStore keyStore = KeystoreUtil.loadKeyStore(
                    GenericConstants.PROTOCOL_CLASSPATH + "/keystore-util-classpath-fallback.p12",
                    PASSWORD,
                    KeystoreUtil.KeystoreFormat.PKCS12.toString());

            assertThat(keyStore.containsAlias("keycloak-test")).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static byte[] createEmptyKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeystoreUtil.KeystoreFormat.PKCS12.toString());
        keyStore.load(null, PASSWORD.toCharArray());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        keyStore.store(output, PASSWORD.toCharArray());
        return output.toByteArray();
    }

    private static final class InMemoryResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceContent;

        private InMemoryResourceClassLoader(String resourceName, byte[] resourceContent) {
            super(null);
            this.resourceName = resourceName;
            this.resourceContent = resourceContent.clone();
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(resourceContent);
            }
            return null;
        }
    }

    private static final class MissingResourceClassLoader extends ClassLoader {
        private MissingResourceClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return null;
        }
    }
}
