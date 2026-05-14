/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractHttp11ProtocolTest {

    @Test
    void connectorReadsHttp11SslFlagThroughPublicApi() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");

        Object sslEnabled = connector.getProperty("SSLEnabled");

        assertThat(sslEnabled).isEqualTo(Boolean.FALSE);
    }
}
