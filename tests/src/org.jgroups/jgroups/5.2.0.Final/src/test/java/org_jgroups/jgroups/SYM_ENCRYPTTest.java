/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.jgroups.protocols.SYM_ENCRYPT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SYM_ENCRYPTTest {
    private static final String KEYSTORE_RESOURCE = "sym-encrypt-test.jceks";
    private static final String ALIAS = "mykey";
    private static final String PASSWORD = "changeit";

    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesSecretKeyFromKeystoreResourceOnContextClassLoader() throws Exception {
        Files.write(temporaryDirectory.resolve(KEYSTORE_RESOURCE), createKeyStore());
        URL[] urls = {temporaryDirectory.toUri().toURL()};
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader resourceLoader = new URLClassLoader(urls, originalLoader)) {
            Thread.currentThread().setContextClassLoader(resourceLoader);
            SYM_ENCRYPT protocol = new SYM_ENCRYPT()
                    .keystoreName(KEYSTORE_RESOURCE)
                    .storePassword(PASSWORD)
                    .alias(ALIAS);

            protocol.init();

            assertThat(protocol.secretKey()).isNotNull();
            assertThat(protocol.secretKey().getAlgorithm()).isEqualTo("AES");
            assertThat(protocol.symVersion()).hasSize(16);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private static byte[] createKeyStore() throws Exception {
        char[] password = PASSWORD.toCharArray();
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, password);

        SecretKey secretKey = new SecretKeySpec(new byte[] {
                0x01, 0x23, 0x45, 0x67,
                0x11, 0x22, 0x33, 0x44,
                0x55, 0x66, 0x77, 0x00,
                0x10, 0x20, 0x30, 0x40
        }, "AES");
        keyStore.setEntry(ALIAS, new KeyStore.SecretKeyEntry(secretKey),
                new KeyStore.PasswordProtection(password));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        keyStore.store(output, password);
        return output.toByteArray();
    }
}
