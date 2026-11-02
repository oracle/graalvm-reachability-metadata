/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT disconnect reason subsystem: an application receives a broker redirect and
 * uses the MQTT 5 reason details to choose the next server.
 */
class MqttDisconnectReasonTest {

    @Test
    void readsBrokerRedirectAndReasonDetails() {
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("redirectable-client")
                    .cleanSession(true)
                    .build();
            assertThat(brokerEncoder.writeOutbound(connect)).isTrue();
            ByteBuf connectPacket = brokerEncoder.readOutbound();
            try {
                assertThat(clientDecoder.writeInbound(connectPacket.retain())).isTrue();
                assertThat(((MqttConnectMessage) clientDecoder.readInbound()).variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
            } finally {
                connectPacket.release();
            }

            MqttProperties properties = new MqttProperties();
            properties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.REASON_STRING.value(),
                    "broker is moving this session"));
            properties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.SERVER_REFERENCE.value(),
                    "mqtt://backup.example.test:1883"));

            MqttMessage disconnect = MqttMessageBuilders.disconnect()
                    .reasonCode(MqttReasonCodes.Disconnect.SERVER_MOVED.byteValue())
                    .properties(properties)
                    .build();

            assertThat(brokerEncoder.writeOutbound(disconnect)).isTrue();
            ByteBuf packet = brokerEncoder.readOutbound();
            try {
                assertThat(clientDecoder.writeInbound(packet.retain())).isTrue();
                MqttMessage receivedDisconnect = clientDecoder.readInbound();
                assertThat(receivedDisconnect.fixedHeader().messageType())
                        .isEqualTo(MqttMessageType.DISCONNECT);

                MqttReasonCodeAndPropertiesVariableHeader header =
                        (MqttReasonCodeAndPropertiesVariableHeader) receivedDisconnect.variableHeader();
                assertThat(header.reasonCode())
                        .isEqualTo(MqttReasonCodes.Disconnect.SERVER_MOVED.byteValue());
                assertThat(header.properties()
                        .getProperty(MqttProperties.MqttPropertyType.REASON_STRING.value()).value())
                        .isEqualTo("broker is moving this session");
                assertThat(header.properties()
                        .getProperty(MqttProperties.MqttPropertyType.SERVER_REFERENCE.value()).value())
                        .isEqualTo("mqtt://backup.example.test:1883");
            } finally {
                packet.release();
            }
        } finally {
            brokerEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }
}
