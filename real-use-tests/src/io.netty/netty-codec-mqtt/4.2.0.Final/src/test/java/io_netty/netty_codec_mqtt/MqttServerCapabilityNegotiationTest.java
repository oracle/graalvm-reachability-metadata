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
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT server capability negotiation subsystem: a client reads the connection
 * limits and assigned identity that a broker returns in its MQTT 5 acknowledgement.
 */
class MqttServerCapabilityNegotiationTest {

    @Test
    void readsBrokerLimitsAndAssignedClientIdentifier() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("gateway")
                    .cleanSession(true)
                    .build();
            assertThat(clientEncoder.writeOutbound(connect)).isTrue();
            ByteBuf connectPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(connectPacket.retainedDuplicate())).isTrue();
                assertThat(clientDecoder.writeInbound(connectPacket.retainedDuplicate())).isTrue();
                assertThat(((MqttConnectMessage) clientDecoder.readInbound()).variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
                brokerDecoder.readInbound();
            } finally {
                connectPacket.release();
            }

            assertThat(brokerEncoder.writeOutbound(connect)).isTrue();
            ByteBuf brokerConnectPacket = brokerEncoder.readOutbound();
            brokerConnectPacket.release();

            MqttProperties brokerProperties = new MqttProperties();
            brokerProperties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER.value(),
                    "gateway-assigned-7"));
            brokerProperties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.RECEIVE_MAXIMUM.value(), 32));
            brokerProperties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.MAXIMUM_PACKET_SIZE.value(), 1048576));
            brokerProperties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.TOPIC_ALIAS_MAXIMUM.value(), 8));
            brokerProperties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE.value(), 1));

            MqttConnAckMessage acknowledgement = MqttMessageBuilders.connAck()
                    .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                    .sessionPresent(false)
                    .properties(brokerProperties)
                    .build();

            assertThat(brokerEncoder.writeOutbound(acknowledgement)).isTrue();
            ByteBuf acknowledgementPacket = brokerEncoder.readOutbound();
            try {
                assertThat(clientDecoder.writeInbound(acknowledgementPacket.retain())).isTrue();
                MqttConnAckMessage receivedAcknowledgement = clientDecoder.readInbound();
                assertThat(receivedAcknowledgement.variableHeader().connectReturnCode())
                        .isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED);
                assertThat(receivedAcknowledgement.variableHeader().isSessionPresent()).isFalse();
                assertThat(stringProperty(receivedAcknowledgement,
                        MqttProperties.MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER))
                        .isEqualTo("gateway-assigned-7");
                assertThat(integerProperty(receivedAcknowledgement,
                        MqttProperties.MqttPropertyType.RECEIVE_MAXIMUM)).isEqualTo(32);
                assertThat(integerProperty(receivedAcknowledgement,
                        MqttProperties.MqttPropertyType.MAXIMUM_PACKET_SIZE)).isEqualTo(1048576);
                assertThat(integerProperty(receivedAcknowledgement,
                        MqttProperties.MqttPropertyType.TOPIC_ALIAS_MAXIMUM)).isEqualTo(8);
                assertThat(integerProperty(receivedAcknowledgement,
                        MqttProperties.MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE)).isEqualTo(1);
            } finally {
                acknowledgementPacket.release();
            }
        } finally {
            clientEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            brokerEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }

    private static String stringProperty(
            MqttConnAckMessage message, MqttProperties.MqttPropertyType propertyType) {
        return message.variableHeader().properties().getProperties(propertyType.value()).get(0)
                .value().toString();
    }

    private static int integerProperty(
            MqttConnAckMessage message, MqttProperties.MqttPropertyType propertyType) {
        List<? extends MqttProperties.MqttProperty> properties =
                message.variableHeader().properties().getProperties(propertyType.value());
        return (Integer) properties.get(0).value();
    }
}
