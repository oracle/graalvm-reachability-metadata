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

import static org.assertj.core.api.Assertions.assertThat;

public class SYM_ENCRYPTTest {
    private static final String KEYSTORE_RESOURCE = "org_jgroups/jgroups/sym-encrypt-keystore.p12";
    private static final String KEYSTORE_TYPE = "PKCS12";
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
    void loadsSecretKeyFromConfiguredKeystoreFile() throws Exception {
        SYM_ENCRYPT protocol = new ConfiguredSYM_ENCRYPT()
                .keystoreType(KEYSTORE_TYPE)
                .keystoreName(KEYSTORE_RESOURCE)
                .storePassword(KEYSTORE_PASSWORD)
                .alias(KEY_ALIAS);

        protocol.init();

        assertThat(protocol.secretKey().getAlgorithm()).isEqualTo("AES");
        assertThat(protocol.secretKey().getEncoded()).isNotEmpty();
        assertThat(protocol.symVersion()).isNotEmpty();
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    private static final class ConfiguredSYM_ENCRYPT extends SYM_ENCRYPT {
        private ConfiguredSYM_ENCRYPT keystoreType(String keystoreType) {
            this.keystore_type = keystoreType;
            return this;
        }
    }
}
