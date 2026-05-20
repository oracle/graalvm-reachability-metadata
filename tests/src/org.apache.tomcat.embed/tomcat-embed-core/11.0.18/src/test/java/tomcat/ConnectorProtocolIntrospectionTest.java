/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectorProtocolIntrospectionTest {

    @ParameterizedTest
    @ValueSource(strings = {"HTTP/1.1", "org.apache.coyote.http11.Http11NioProtocol",
            "org.apache.coyote.http11.Http11Nio2Protocol"})
    void connectorReadsProtocolHandlerPropertiesThroughPublicApi(String protocol) throws Exception {
        Connector connector = new Connector(protocol);
        connector.setPort(0);

        try {
            connector.init();
            connector.start();

            Object protocolName = connector.getProperty("name");
            Object missingProtocolProperty = connector.getProperty("missingPropertyForReachabilityMetadata");
            Object sslEnabled = connector.getProperty("SSLEnabled");

            assertThat(protocolName).isInstanceOf(String.class);
            assertThat(protocolName.toString()).isNotBlank();
            assertThat(missingProtocolProperty).isNull();
            assertThat(sslEnabled).isEqualTo(Boolean.FALSE);
        } finally {
            stopAndDestroy(connector);
        }
    }

    private static void stopAndDestroy(Connector connector) throws LifecycleException {
        try {
            if (connector.getState().isAvailable()) {
                connector.stop();
            }
        } finally {
            if (connector.getState() != LifecycleState.DESTROYED) {
                connector.destroy();
            }
        }
    }
}
