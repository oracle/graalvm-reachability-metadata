/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT publish acknowledgement subsystem: an application publishes a QoS 1
 * command and confirms that the broker acknowledges the packet identifier and delivery result.
 */
class MqttPublishAcknowledgementTest {

    @Test
    void publishesCommandAndAcceptsBrokerAcknowledgement() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            ByteBuf commandPayload = Unpooled.copiedBuffer("{\"command\":\"start\"}", StandardCharsets.UTF_8);
            MqttPublishMessage command = MqttMessageBuilders.publish()
                    .topicName("building/boiler-room/commands")
                    .qos(MqttQoS.AT_LEAST_ONCE)
                    .messageId(604)
                    .properties(new MqttProperties())
                    .payload(commandPayload)
                    .build();

            assertThat(clientEncoder.writeOutbound(command)).isTrue();
            ByteBuf commandPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(commandPacket.retain())).isTrue();
                MqttPublishMessage receivedCommand = brokerDecoder.readInbound();
                try {
                    assertThat(receivedCommand.fixedHeader().messageType())
                            .isEqualTo(MqttMessageType.PUBLISH);
                    assertThat(receivedCommand.fixedHeader().qosLevel()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
                    assertThat(receivedCommand.variableHeader().topicName())
                            .isEqualTo("building/boiler-room/commands");
                    assertThat(receivedCommand.variableHeader().messageId()).isEqualTo(604);
                    assertThat(receivedCommand.payload().toString(StandardCharsets.UTF_8))
                            .isEqualTo("{\"command\":\"start\"}");

                    MqttMessage acknowledgement = MqttMessageBuilders.pubAck()
                            .packetId(receivedCommand.variableHeader().messageId())
                            .reasonCode(MqttReasonCodes.PubAck.SUCCESS.byteValue())
                            .properties(new MqttProperties())
                            .build();
                    assertThat(brokerEncoder.writeOutbound(acknowledgement)).isTrue();
                    ByteBuf acknowledgementPacket = brokerEncoder.readOutbound();
                    try {
                        assertThat(clientDecoder.writeInbound(acknowledgementPacket.retain())).isTrue();
                        MqttMessage receivedAcknowledgement = clientDecoder.readInbound();
                        MqttPubReplyMessageVariableHeader header =
                                (MqttPubReplyMessageVariableHeader) receivedAcknowledgement.variableHeader();
                        assertThat(receivedAcknowledgement.fixedHeader().messageType())
                                .isEqualTo(MqttMessageType.PUBACK);
                        assertThat(header.messageId()).isEqualTo(604);
                        assertThat(header.reasonCode()).isEqualTo(MqttReasonCodes.PubAck.SUCCESS.byteValue());
                    } finally {
                        acknowledgementPacket.release();
                    }
                } finally {
                    receivedCommand.release();
                }
            } finally {
                commandPacket.release();
            }
        } finally {
            clientEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            brokerEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }
}
