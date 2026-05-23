/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;

import org.junit.jupiter.api.Test;
import org.keycloak.common.constants.GenericConstants;
import org.keycloak.common.util.KeystoreUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class KeystoreUtilTest {
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String KEYSTORE_TYPE = KeystoreUtil.KeystoreFormat.PKCS12.toString();
    private static final String CLASSPATH_KEYSTORE_RESOURCE =
            "org_keycloak/keycloak_client_common_synced/classpath-keystore.p12";
    private static final String CONTEXT_KEYSTORE_RESOURCE =
            "org_keycloak/keycloak_client_common_synced/context-class-loader-keystore.p12";

    @Test
    void loadsClasspathKeystoreFromContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        ClassLoader contextClassLoader = new InMemoryKeyStoreClassLoader(CONTEXT_KEYSTORE_RESOURCE);

        try {
            thread.setContextClassLoader(contextClassLoader);

            KeyStore keyStore = KeystoreUtil.loadKeyStore(
                    GenericConstants.PROTOCOL_CLASSPATH + CONTEXT_KEYSTORE_RESOURCE,
                    KEYSTORE_PASSWORD,
                    KEYSTORE_TYPE);

            assertThat(keyStore.size()).isZero();
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void loadsClasspathKeystoreFromLibraryClassResourceFallback() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(null);

            KeyStore keyStore = KeystoreUtil.loadKeyStore(
                    GenericConstants.PROTOCOL_CLASSPATH + "/" + CLASSPATH_KEYSTORE_RESOURCE,
                    KEYSTORE_PASSWORD,
                    KEYSTORE_TYPE);

            assertThat(keyStore.size()).isZero();
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static byte[] createEmptyKeyStoreBytes() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        char[] password = KEYSTORE_PASSWORD.toCharArray();
        keyStore.load(null, password);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            keyStore.store(outputStream, password);
            return outputStream.toByteArray();
        }
    }

    private static final class InMemoryKeyStoreClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] keyStoreBytes;

        private InMemoryKeyStoreClassLoader(String resourceName) throws Exception {
            super(null);
            this.resourceName = resourceName;
            this.keyStoreBytes = createEmptyKeyStoreBytes();
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(keyStoreBytes);
            }
            return null;
        }
    }
}
