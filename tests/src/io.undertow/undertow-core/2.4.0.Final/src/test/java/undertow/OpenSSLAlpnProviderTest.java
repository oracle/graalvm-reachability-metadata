/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.protocols.alpn.OpenSSLAlpnProvider;
import org.junit.jupiter.api.Test;
import org.wildfly.openssl.OpenSSLEngine;

import javax.net.ssl.SSLEngine;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenSSLAlpnProviderTest {

    @Test
    void openSslEngineSupportsAlpnProtocolSelection() {
        OpenSSLAlpnProvider provider = new OpenSSLAlpnProvider();
        OpenSSLEngine engine = new OpenSSLEngine();
        String[] protocols = {"h2", "http/1.1"};

        SSLEngine configuredEngine = provider.setProtocols(engine, protocols);

        assertThat(provider.isEnabled(engine)).isTrue();
        assertThat(configuredEngine).isSameAs(engine);
        assertThat(engine.applicationProtocols()).containsExactly(protocols);
        assertThat(provider.getSelectedProtocol(engine)).isEqualTo("h2");
    }
}
