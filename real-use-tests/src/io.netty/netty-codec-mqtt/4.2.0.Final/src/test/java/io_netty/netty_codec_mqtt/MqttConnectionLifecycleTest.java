/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT connection lifecycle subsystem: a client negotiates an MQTT 5 session,
 * keeps it alive with a ping exchange, and closes it after the broker accepts the connection.
 */
class MqttConnectionLifecycleTest {

    @Test
    void negotiatesSessionKeepsItAliveAndDisconnects() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttProperties connectProperties = new MqttProperties();
            connectProperties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), 120));
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("telemetry-gateway")
                    .cleanSession(false)
                    .keepAlive(30)
                    .willFlag(true)
                    .willQoS(MqttQoS.AT_LEAST_ONCE)
                    .willRetain(true)
                    .willTopic("building/gateway/status")
                    .willMessage("offline")
                    .hasUser(true)
                    .hasPassword(true)
                    .username("gateway")
                    .password("secret")
                    .properties(connectProperties)
                    .build();

            assertThat(clientEncoder.writeOutbound(connect)).isTrue();
            ByteBuf connectPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(connectPacket.retain())).isTrue();
                MqttConnectMessage receivedConnect = brokerDecoder.readInbound();
                assertThat(receivedConnect.variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
                assertThat(receivedConnect.variableHeader().isCleanSession()).isFalse();
                assertThat(receivedConnect.variableHeader().keepAliveTimeSeconds()).isEqualTo(30);
                assertThat(receivedConnect.payload().clientIdentifier()).isEqualTo("telemetry-gateway");
                assertThat(receivedConnect.payload().userName()).isEqualTo("gateway");
                assertThat(receivedConnect.payload().password()).isEqualTo("secret");
                assertThat(receivedConnect.payload().willTopic())
                        .isEqualTo("building/gateway/status");
                assertThat(receivedConnect.payload().willMessage()).isEqualTo("offline");
                assertThat(receivedConnect.variableHeader().properties()
                        .getProperties(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value()))
                        .hasSize(1);

                MqttConnAckMessage connAck = MqttMessageBuilders.connAck()
                        .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                        .sessionPresent(true)
                        .properties(new MqttProperties())
                        .build();
                assertThat(brokerEncoder.writeOutbound(connAck)).isTrue();
                ByteBuf connAckPacket = brokerEncoder.readOutbound();
                try {
                    assertThat(clientDecoder.writeInbound(connAckPacket.retain())).isTrue();
                    MqttConnAckMessage receivedConnAck = clientDecoder.readInbound();
                    assertThat(receivedConnAck.variableHeader().connectReturnCode())
                            .isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED);
                    assertThat(receivedConnAck.variableHeader().isSessionPresent()).isTrue();
                } finally {
                    connAckPacket.release();
                }
            } finally {
                connectPacket.release();
            }

            assertThat(clientEncoder.writeOutbound(MqttMessage.PINGREQ)).isTrue();
            ByteBuf pingRequestPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(pingRequestPacket.retain())).isTrue();
                MqttMessage pingRequest = brokerDecoder.readInbound();
                assertThat(pingRequest.fixedHeader().messageType()).isEqualTo(MqttMessageType.PINGREQ);

                assertThat(brokerEncoder.writeOutbound(MqttMessage.PINGRESP)).isTrue();
                ByteBuf pingResponsePacket = brokerEncoder.readOutbound();
                try {
                    assertThat(clientDecoder.writeInbound(pingResponsePacket.retain())).isTrue();
                    MqttMessage pingResponse = clientDecoder.readInbound();
                    assertThat(pingResponse.fixedHeader().messageType())
                            .isEqualTo(MqttMessageType.PINGRESP);
                } finally {
                    pingResponsePacket.release();
                }
            } finally {
                pingRequestPacket.release();
            }

            assertThat(clientEncoder.writeOutbound(MqttMessageBuilders.disconnect().build())).isTrue();
            ByteBuf disconnectPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(disconnectPacket.retain())).isTrue();
                MqttMessage disconnect = brokerDecoder.readInbound();
                assertThat(disconnect.fixedHeader().messageType()).isEqualTo(MqttMessageType.DISCONNECT);
            } finally {
                disconnectPacket.release();
            }
        } finally {
            clientEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            brokerEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }
}
