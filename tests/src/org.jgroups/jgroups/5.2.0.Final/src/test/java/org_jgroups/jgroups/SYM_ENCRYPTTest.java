/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.jgroups.protocols.SYM_ENCRYPT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SYM_ENCRYPTTest {
    private static final byte[] KEYSTORE_CONTENT = Base64.getMimeDecoder().decode("""
            MIIBjwIBAzCCATkGCSqGSIb3DQEHAaCCASoEggEmMIIBIjCCAR4GCSqGSIb3DQEHAaCCAQ8EggELMIIBBzCC
            AQMGCyqGSIb3DQEMCgEFoIGzMIGwBgsqhkiG9w0BDAoBAqCBoASBnTCBmjBmBgkqhkiG9w0BBQ0wWTA4Bgkq
            hkiG9w0BBQwwKwQUwnyJecKkdFgj1z2xXdwwB4qP+rcCAicQAgEgMAwGCCqGSIb3DQIJBQAwHQYJYIZIAWUD
            BAEqBBDxnSGLcfCFCGIfHt5cXnu8BDDTvX2ratxrfzSN85ynRBCMa4y749WLhzujLIf8B4m2Phz82HHOErNp
            iLgycxzb274xPjAZBgkqhkiG9w0BCRQxDB4KAG0AeQBrAGUAeTAhBgkqhkiG9w0BCRUxFAQSVGltZSAxNzc5
            NDk2OTE3NTE4ME0wMTANBglghkgBZQMEAgEFAAQg1gaLgHqiwM36MJNzZ1czKTdmtGWoW8upJt8PxpACaUYE
            FHqXTV4Rk78nZo8iN6SmckbpNogRAgInEA==
            """);
    private static final String KEYSTORE_RESOURCE = "sym-encrypt-test.p12";
    private static final String ALIAS = "mykey";
    private static final String PASSWORD = "changeit";

    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesSecretKeyFromKeystoreResourceOnContextClassLoader() throws Exception {
        Files.write(temporaryDirectory.resolve(KEYSTORE_RESOURCE), KEYSTORE_CONTENT);
        URL[] urls = {temporaryDirectory.toUri().toURL()};
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader resourceLoader = new URLClassLoader(urls, originalLoader)) {
            Thread.currentThread().setContextClassLoader(resourceLoader);
            ConfigurableSymEncrypt protocol = new ConfigurableSymEncrypt();
            protocol.keystoreType("PKCS12");
            protocol.keystoreName(KEYSTORE_RESOURCE);
            protocol.storePassword(PASSWORD);
            protocol.alias(ALIAS);

            protocol.init();

            assertThat(protocol.secretKey()).isNotNull();
            assertThat(protocol.secretKey().getAlgorithm()).isEqualTo("AES");
            assertThat(protocol.symVersion()).hasSize(16);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private static final class ConfigurableSymEncrypt extends SYM_ENCRYPT {
        private ConfigurableSymEncrypt keystoreType(String keystoreType) {
            this.keystore_type = keystoreType;
            return this;
        }
    }
}
