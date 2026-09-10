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
import org.apache.coyote.Request;
import org.apache.coyote.http2.Http2Protocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectorProtocolIntrospectionTest {

    @Test
    void http2ProtocolExposesCleartextAndTlsNegotiationIdentifiers() {
        Http2Protocol protocol = new Http2Protocol();

        assertThat(protocol.getHttpUpgradeName(false)).isEqualTo("h2c");
        assertThat(protocol.getHttpUpgradeName(true)).isNull();
        assertThat(protocol.getAlpnName()).isEqualTo("h2");
        assertThat(protocol.getAlpnIdentifier()).containsExactly((byte) 'h', (byte) '2');
    }

    @Test
    void http2ProtocolValidatesCleartextUpgradeSettingsHeaders() {
        Http2Protocol protocol = new Http2Protocol();
        Request validRequest = new Request();
        validRequest.getMimeHeaders().addValue("Connection").setString("keep-alive, HTTP2-Settings");
        validRequest.getMimeHeaders().addValue("HTTP2-Settings").setString("encoded-settings");

        assertThat(protocol.accept(validRequest)).isTrue();

        Request missingSettingsRequest = new Request();
        missingSettingsRequest.getMimeHeaders().addValue("Connection").setString("HTTP2-Settings");
        assertThat(protocol.accept(missingSettingsRequest)).isFalse();

        validRequest.getMimeHeaders().addValue("HTTP2-Settings").setString("duplicate-settings");
        assertThat(protocol.accept(validRequest)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP/1.1", "org.apache.coyote.http11.Http11NioProtocol",
            "org.apache.coyote.http11.Http11Nio2Protocol"})
    void connectorReadsProtocolHandlerPropertiesThroughPublicApi(String protocol) throws Exception {
        Connector connector = new Connector(protocol);
        connector.setPort(0);

        try {
            Object bindOnInitBeforeUpdate = connector.getProperty("bindOnInit");
            boolean bindOnInitUpdated = connector.setProperty("bindOnInit", "false");
            Object bindOnInitAfterUpdate = connector.getProperty("bindOnInit");

            assertThat(bindOnInitBeforeUpdate).isNull();
            assertThat(bindOnInitUpdated).isTrue();
            assertThat(bindOnInitAfterUpdate).isEqualTo("false");

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
