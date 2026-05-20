/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.IntrospectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionUtilsTest {

    @Test
    void invokesProtocolAndConnectorAccessors() throws Exception {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        ProtocolHandler protocolHandler = connector.getProtocolHandler();

        assertThat(IntrospectionUtils.setProperty(connector, "port", "0")).isTrue();
        assertThat(IntrospectionUtils.setProperty(protocolHandler, "relaxedPathChars", "[]")).isTrue();
        assertThat(IntrospectionUtils.getProperty(protocolHandler, "name")).isNotNull();
        assertThat(IntrospectionUtils.getProperty(protocolHandler, "SSLEnabled")).isEqualTo(Boolean.FALSE);
        assertThat(IntrospectionUtils.getProperty(protocolHandler, "relaxedPathChars")).isEqualTo("[]");
        assertThat(IntrospectionUtils.callMethodN(connector, "getProtocol", new Object[0], new Class<?>[0]))
                .isEqualTo("org.apache.coyote.http11.Http11NioProtocol");
    }
}
