/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.SYM_ENCRYPT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;

public class SYM_ENCRYPTTest {
    private static final String KEYSTORE_TYPE = "JCEKS";
    private static final String KEY_ALIAS = "mykey";
    private static final String KEYSTORE_PASSWORD = "changeit";

    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void loadsSecretKeyFromConfiguredKeystoreFile(@TempDir Path tempDir) throws Exception {
        SecretKey expectedKey = createSecretKey();
        Path keystore = tempDir.resolve("sym-encrypt.jceks");
        writeSecretKeyStore(keystore, expectedKey);

        SYM_ENCRYPT protocol = new SYM_ENCRYPT()
                .keystoreName(keystore.toString())
                .storePassword(KEYSTORE_PASSWORD)
                .alias(KEY_ALIAS);

        protocol.init();

        assertThat(protocol.secretKey().getAlgorithm()).isEqualTo("AES");
        assertThat(protocol.secretKey().getEncoded()).isEqualTo(expectedKey.getEncoded());
        assertThat(protocol.symVersion()).isNotEmpty();
    }

    private static SecretKey createSecretKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        return keyGenerator.generateKey();
    }

    private static void writeSecretKeyStore(Path keystore, SecretKey key) throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE_TYPE);
        char[] password = KEYSTORE_PASSWORD.toCharArray();
        store.load(null, password);
        store.setEntry(
                KEY_ALIAS,
                new KeyStore.SecretKeyEntry(key),
                new KeyStore.PasswordProtection(password));
        try (OutputStream outputStream = Files.newOutputStream(keystore)) {
            store.store(outputStream, password);
        }
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }
}
