/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.wildfly.openssl.OpenSSLProvider;
import org.wildfly.openssl.SSL;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import static org.assertj.core.api.Assertions.assertThat;

public class SslContextFactoryTest {
    @Test
    void discoversAndInitializesAvailableOpenSslProvider() {
        assertThat(SslContextFactory.getSslProvider()).isEqualTo("openssl");
        assertThat(OpenSSLProvider.registerCount()).isGreaterThanOrEqualTo(1);
        assertThat(SSL.getInstanceCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void createsConfiguredEnginesFromSuppliedContext() throws Exception {
        SSLContext context = SSLContext.getInstance(SslContextFactory.getDefaultSslProtocol());
        context.init(null, null, null);

        SSLEngine engine = SslContextFactory.getEngine(context, true, true);

        assertThat(engine.getUseClientMode()).isTrue();
        assertThat(engine.getNeedClientAuth()).isTrue();
    }
}
