/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.jgroups.util.SslContextFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SslContextFactoryTest {
    @Test
    void initializesSslContextAndEngineUsingPublicFactoryApi() throws Exception {
        String defaultProtocol = SslContextFactory.getDefaultSslProtocol();
        String providerName = SSLContext.getInstance(defaultProtocol).getProvider().getName();
        assertThat(defaultProtocol).isEqualTo("TLSv1.2");

        SSLContext context = new SslContextFactory()
                .sslProvider(providerName)
                .sslProtocol(defaultProtocol)
                .getContext();
        SSLEngine engine = SslContextFactory.getEngine(context, true, false);

        assertThat(context.getProtocol()).isEqualTo(defaultProtocol);
        assertThat(engine.getUseClientMode()).isTrue();
        assertThat(engine.getNeedClientAuth()).isFalse();
    }
}
