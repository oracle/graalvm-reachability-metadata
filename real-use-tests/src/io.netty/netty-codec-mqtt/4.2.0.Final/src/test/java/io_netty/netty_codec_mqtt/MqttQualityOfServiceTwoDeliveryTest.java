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
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT exactly-once delivery subsystem: an application completes the four-packet
 * QoS 2 handshake before processing a command payload.
 */
class MqttQualityOfServiceTwoDeliveryTest {

    @Test
    void completesExactlyOnceCommandDelivery() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttPublishMessage command = MqttMessageBuilders.publish()
                    .topicName("building/boiler-room/commands")
                    .qos(MqttQoS.EXACTLY_ONCE)
                    .messageId(271)
                    .properties(new MqttProperties())
                    .payload(Unpooled.copiedBuffer("{\"command\":\"start\"}", StandardCharsets.UTF_8))
                    .build();

            assertThat(clientEncoder.writeOutbound(command)).isTrue();
            ByteBuf publishPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(publishPacket.retain())).isTrue();
                MqttPublishMessage receivedPublish = brokerDecoder.readInbound();
                try {
                    assertThat(receivedPublish.fixedHeader().qosLevel())
                            .isEqualTo(MqttQoS.EXACTLY_ONCE);
                    assertThat(receivedPublish.variableHeader().messageId()).isEqualTo(271);
                    assertThat(receivedPublish.payload().toString(StandardCharsets.UTF_8))
                            .isEqualTo("{\"command\":\"start\"}");

                    assertThat(brokerEncoder.writeOutbound(pubReply(
                            MqttMessageType.PUBREC,
                            271,
                            MqttReasonCodes.PubRec.SUCCESS.byteValue()))).isTrue();
                    ByteBuf pubRecPacket = brokerEncoder.readOutbound();
                    try {
                        assertThat(clientDecoder.writeInbound(pubRecPacket.retain())).isTrue();
                        MqttMessage receivedPubRec = clientDecoder.readInbound();
                        assertThat(receivedPubRec.fixedHeader().messageType())
                                .isEqualTo(MqttMessageType.PUBREC);
                        assertThat(replyHeader(receivedPubRec).messageId()).isEqualTo(271);
                        assertThat(replyHeader(receivedPubRec).reasonCode())
                                .isEqualTo(MqttReasonCodes.PubRec.SUCCESS.byteValue());
                    } finally {
                        pubRecPacket.release();
                    }

                    assertThat(clientEncoder.writeOutbound(pubReply(
                            MqttMessageType.PUBREL,
                            271,
                            MqttReasonCodes.PubRel.SUCCESS.byteValue()))).isTrue();
                    ByteBuf pubRelPacket = clientEncoder.readOutbound();
                    try {
                        assertThat(brokerDecoder.writeInbound(pubRelPacket.retain())).isTrue();
                        MqttMessage receivedPubRel = brokerDecoder.readInbound();
                        assertThat(receivedPubRel.fixedHeader().messageType())
                                .isEqualTo(MqttMessageType.PUBREL);
                        assertThat(receivedPubRel.fixedHeader().qosLevel())
                                .isEqualTo(MqttQoS.AT_LEAST_ONCE);
                        assertThat(replyHeader(receivedPubRel).messageId()).isEqualTo(271);
                        assertThat(replyHeader(receivedPubRel).reasonCode())
                                .isEqualTo(MqttReasonCodes.PubRel.SUCCESS.byteValue());

                        assertThat(brokerEncoder.writeOutbound(pubReply(
                                MqttMessageType.PUBCOMP,
                                271,
                                MqttReasonCodes.PubComp.SUCCESS.byteValue()))).isTrue();
                        ByteBuf pubCompPacket = brokerEncoder.readOutbound();
                        try {
                            assertThat(clientDecoder.writeInbound(pubCompPacket.retain())).isTrue();
                            MqttMessage receivedPubComp = clientDecoder.readInbound();
                            assertThat(receivedPubComp.fixedHeader().messageType())
                                    .isEqualTo(MqttMessageType.PUBCOMP);
                            assertThat(replyHeader(receivedPubComp).messageId()).isEqualTo(271);
                            assertThat(replyHeader(receivedPubComp).reasonCode())
                                    .isEqualTo(MqttReasonCodes.PubComp.SUCCESS.byteValue());
                        } finally {
                            pubCompPacket.release();
                        }
                    } finally {
                        pubRelPacket.release();
                    }
                } finally {
                    receivedPublish.release();
                }
            } finally {
                publishPacket.release();
            }
        } finally {
            clientEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            brokerEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }

    private static MqttMessage pubReply(MqttMessageType type, int packetId, byte reasonCode) {
        MqttQoS qos = type == MqttMessageType.PUBREL ? MqttQoS.AT_LEAST_ONCE : MqttQoS.AT_MOST_ONCE;
        MqttFixedHeader fixedHeader = new MqttFixedHeader(type, false, qos, false, 0);
        MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(
                packetId, reasonCode, new MqttProperties());
        return new MqttMessage(fixedHeader, variableHeader);
    }

    private static MqttPubReplyMessageVariableHeader replyHeader(MqttMessage message) {
        return (MqttPubReplyMessageVariableHeader) message.variableHeader();
    }
}
