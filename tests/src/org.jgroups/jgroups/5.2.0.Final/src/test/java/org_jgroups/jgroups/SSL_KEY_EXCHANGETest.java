/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.CertficateCNMatcher;
import org.jgroups.protocols.SSL_KEY_EXCHANGE;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;

import static org.assertj.core.api.Assertions.assertThat;

public class SSL_KEY_EXCHANGETest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void createsConfiguredSessionVerifierDuringInitialization() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, null, null);

        SSL_KEY_EXCHANGE keyExchange = new SSL_KEY_EXCHANGE()
                .setClientSSLContext(sslContext)
                .setServerSSLContext(sslContext)
                .setSessionVerifierClass(CertficateCNMatcher.class.getName())
                .setSessionVerifierArg("CN=.*")
                .setSocketTimeout(10_000);

        keyExchange.init();

        assertThat(keyExchange.getSessionVerifier()).isInstanceOf(CertficateCNMatcher.class);
        assertThat(keyExchange.getSessionVerifierClass()).isEqualTo(CertficateCNMatcher.class.getName());
        assertThat(keyExchange.getSessionVerifierArg()).isEqualTo("CN=.*");
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }
}
