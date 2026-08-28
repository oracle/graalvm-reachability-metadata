/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectorProtocolIntrospectionTest {

    @Test
    void connectorReadsAndUpdatesBindOnInitThroughPublicApi() {
        Connector connector = new Connector();

        Object bindOnInitBeforeUpdate = connector.getProperty("bindOnInit");
        boolean bindOnInitUpdated = connector.setProperty("bindOnInit", "false");
        Object bindOnInitAfterUpdate = connector.getProperty("bindOnInit");

        assertThat(bindOnInitBeforeUpdate).isNull();
        assertThat(bindOnInitUpdated).isTrue();
        assertThat(bindOnInitAfterUpdate).isEqualTo("false");
    }

    @Test
    void connectorRegistersHttp2UpgradeProtocol() {
        Connector connector = new Connector();
        Http2Protocol http2Protocol = new Http2Protocol();

        connector.addUpgradeProtocol(http2Protocol);

        assertThat(connector.findUpgradeProtocols()).containsExactly(http2Protocol);
        assertThat(http2Protocol.getHttpUpgradeName(false)).isEqualTo("h2c");
        assertThat(http2Protocol.getAlpnName()).isEqualTo("h2");
    }

    @Test
    void connectorRegistersNamedSslHostConfiguration() {
        Connector connector = new Connector();
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setHostName("api.example.test");
        sslHostConfig.setProtocols("TLSv1.3");

        connector.addSslHostConfig(sslHostConfig);

        assertThat(connector.findSslHostConfigs()).containsExactly(sslHostConfig);
        assertThat(sslHostConfig.getHostName()).isEqualTo("api.example.test");
        assertThat(sslHostConfig.getProtocols()).containsExactly("TLSv1.3");
    }

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
