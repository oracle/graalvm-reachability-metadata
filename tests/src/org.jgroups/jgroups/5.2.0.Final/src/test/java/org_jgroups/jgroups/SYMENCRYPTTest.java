/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.SYM_ENCRYPT;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class SYMENCRYPTTest {
    private static final String KEYSTORE_RESOURCE = "org_jgroups/jgroups/sym-encrypt-test.jceks";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String KEY_ALIAS = "mykey";

    @Test
    void loadsSecretKeyFromContextClassLoaderResource() throws Exception {
        byte[] keystoreContent = createKeyStoreContent();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ResourceClassLoader resourceClassLoader = new ResourceClassLoader(
            originalContextClassLoader,
            KEYSTORE_RESOURCE,
            keystoreContent);
        SYM_ENCRYPT encryption = new SYM_ENCRYPT()
            .keystoreName(KEYSTORE_RESOURCE)
            .storePassword(KEYSTORE_PASSWORD)
            .alias(KEY_ALIAS);

        try {
            Thread.currentThread().setContextClassLoader(resourceClassLoader);

            encryption.init();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }

        assertThat(resourceClassLoader.requestedResource()).isEqualTo(KEYSTORE_RESOURCE);
        assertThat(encryption.secretKey()).isNotNull();
        assertThat(encryption.secretKey().getAlgorithm()).isEqualTo("AES");
        assertThat(encryption.symVersion()).isNotEmpty();
    }

    private static byte[] createKeyStoreContent() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        char[] password = KEYSTORE_PASSWORD.toCharArray();
        SecretKeySpec secretKey = new SecretKeySpec(new byte[16], "AES");
        KeyStore.SecretKeyEntry keyEntry = new KeyStore.SecretKeyEntry(secretKey);

        keyStore.load(null, password);
        keyStore.setEntry(KEY_ALIAS, keyEntry, new KeyStore.PasswordProtection(password));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        keyStore.store(outputStream, password);
        return outputStream.toByteArray();
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceContent;
        private final AtomicReference<String> requestedResource = new AtomicReference<>();

        private ResourceClassLoader(ClassLoader parent, String resourceName, byte[] resourceContent) {
            super(parent);
            this.resourceName = resourceName;
            this.resourceContent = resourceContent;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResource.set(name);
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(resourceContent);
            }
            return super.getResourceAsStream(name);
        }

        private String requestedResource() {
            return requestedResource.get();
        }
    }
}
