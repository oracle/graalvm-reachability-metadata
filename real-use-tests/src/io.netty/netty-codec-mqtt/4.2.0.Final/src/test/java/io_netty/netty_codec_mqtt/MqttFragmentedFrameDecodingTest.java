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
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT fragmented-frame decoding subsystem: an application receives one publish
 * packet in multiple network buffers and processes it only after the frame is complete.
 */
class MqttFragmentedFrameDecodingTest {

    @Test
    void reassemblesPublishPacketSplitAcrossNetworkBuffers() {
        EmbeddedChannel encoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel decoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttPublishMessage outboundMessage = MqttMessageBuilders.publish()
                    .topicName("building/boiler-room/events")
                    .qos(MqttQoS.AT_MOST_ONCE)
                    .properties(new MqttProperties())
                    .payload(Unpooled.copiedBuffer(
                            "{\"event\":\"door-open\"}", StandardCharsets.UTF_8))
                    .build();
            assertThat(encoder.writeOutbound(outboundMessage)).isTrue();
            ByteBuf wirePacket = encoder.readOutbound();
            int firstFrameLength = 2;
            ByteBuf firstFrame = wirePacket.retainedSlice(wirePacket.readerIndex(), firstFrameLength);
            ByteBuf secondFrame = wirePacket.retainedSlice(
                    wirePacket.readerIndex() + firstFrameLength,
                    wirePacket.readableBytes() - firstFrameLength);
            try {
                assertThat(decoder.writeInbound(firstFrame)).isFalse();
                Object incompleteMessage = decoder.readInbound();
                assertThat(incompleteMessage).isNull();

                assertThat(decoder.writeInbound(secondFrame)).isTrue();
                MqttPublishMessage receivedMessage = decoder.readInbound();
                try {
                    assertThat(receivedMessage.fixedHeader().messageType())
                            .isEqualTo(MqttMessageType.PUBLISH);
                    assertThat(receivedMessage.fixedHeader().qosLevel())
                            .isEqualTo(MqttQoS.AT_MOST_ONCE);
                    assertThat(receivedMessage.variableHeader().topicName())
                            .isEqualTo("building/boiler-room/events");
                    assertThat(receivedMessage.payload().toString(StandardCharsets.UTF_8))
                            .isEqualTo("{\"event\":\"door-open\"}");
                } finally {
                    receivedMessage.release();
                }
            } finally {
                wirePacket.release();
            }
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }
}
