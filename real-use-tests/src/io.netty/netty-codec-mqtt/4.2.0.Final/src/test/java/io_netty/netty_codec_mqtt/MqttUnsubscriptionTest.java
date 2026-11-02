/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT unsubscription subsystem: a client removes obsolete topic filters and
 * confirms that the broker accepted each removal.
 */
class MqttUnsubscriptionTest {

    @Test
    void removesObsoleteTopicFiltersAndReadsBrokerAcknowledgement() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttUnsubscribeMessage unsubscribe = MqttMessageBuilders.unsubscribe()
                    .messageId(913)
                    .addTopicFilter("building/boiler-room/telemetry")
                    .addTopicFilter("building/+/alerts")
                    .build();

            assertThat(clientEncoder.writeOutbound(unsubscribe)).isTrue();
            ByteBuf unsubscribePacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(unsubscribePacket.retain())).isTrue();
                MqttUnsubscribeMessage receivedUnsubscribe = brokerDecoder.readInbound();
                assertThat(receivedUnsubscribe.variableHeader().messageId()).isEqualTo(913);
                assertThat(receivedUnsubscribe.payload().topics())
                        .containsExactly(
                                "building/boiler-room/telemetry",
                                "building/+/alerts");

                MqttUnsubAckMessage acknowledgement = MqttMessageBuilders.unsubAck()
                        .packetId(receivedUnsubscribe.variableHeader().messageId())
                        .properties(new MqttProperties())
                        .addReasonCode(MqttReasonCodes.UnsubAck.SUCCESS.byteValue())
                        .addReasonCode(MqttReasonCodes.UnsubAck.NO_SUBSCRIPTION_EXISTED.byteValue())
                        .build();
                assertThat(brokerEncoder.writeOutbound(acknowledgement)).isTrue();
                ByteBuf acknowledgementPacket = brokerEncoder.readOutbound();
                try {
                    assertThat(clientDecoder.writeInbound(acknowledgementPacket.retain())).isTrue();
                    MqttUnsubAckMessage receivedAcknowledgement = clientDecoder.readInbound();
                    assertThat(receivedAcknowledgement.variableHeader().messageId()).isEqualTo(913);
                    assertThat(receivedAcknowledgement.payload().typedReasonCodes())
                            .containsExactly(
                                    MqttReasonCodes.UnsubAck.SUCCESS,
                                    MqttReasonCodes.UnsubAck.NO_SUBSCRIPTION_EXISTED);
                } finally {
                    acknowledgementPacket.release();
                }
            } finally {
                unsubscribePacket.release();
            }
        } finally {
            clientEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            brokerEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }
}
