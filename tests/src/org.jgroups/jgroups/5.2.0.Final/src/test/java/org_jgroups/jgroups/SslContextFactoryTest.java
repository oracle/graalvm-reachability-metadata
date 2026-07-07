/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.SslContextFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import static org.assertj.core.api.Assertions.assertThat;

public class SslContextFactoryTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void initializesSslContextAndEngineWithJdkProvider() throws Exception {
        String protocol = "TLSv1.2";
        String jdkProviderName = SSLContext.getInstance(protocol).getProvider().getName();

        assertThat(SslContextFactory.getDefaultSslProtocol()).isEqualTo(protocol);

        SSLContext sslContext = new SslContextFactory()
                .sslProtocol(protocol)
                .sslProvider(jdkProviderName)
                .getContext();
        SSLEngine sslEngine = SslContextFactory.getEngine(sslContext, true, false);

        assertThat(sslContext.getProtocol()).isEqualTo(protocol);
        assertThat(sslEngine.getUseClientMode()).isTrue();
        assertThat(sslEngine.getNeedClientAuth()).isFalse();
        assertThat(SslContextFactory.getSslProvider()).isIn((String) null, "openssl");
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }
}
