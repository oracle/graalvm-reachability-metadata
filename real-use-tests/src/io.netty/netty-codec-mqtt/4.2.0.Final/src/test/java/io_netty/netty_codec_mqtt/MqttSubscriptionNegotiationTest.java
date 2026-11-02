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
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT subscription negotiation subsystem: an application sends subscriptions with
 * delivery options and consumes the broker's acknowledgement of the granted quality of service.
 */
class MqttSubscriptionNegotiationTest {

    @Test
    void negotiatesTopicFiltersAndGrantedQualityOfService() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("telemetry-gateway")
                    .cleanSession(true)
                    .keepAlive(30)
                    .build();
            assertThat(clientEncoder.writeOutbound(connect)).isTrue();
            ByteBuf connectPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(connectPacket.retainedDuplicate())).isTrue();
                assertThat(((MqttConnectMessage) brokerDecoder.readInbound())
                        .variableHeader().version()).isEqualTo(MqttVersion.MQTT_5.protocolLevel());
                assertThat(clientDecoder.writeInbound(connectPacket.retainedDuplicate())).isTrue();
                assertThat(((MqttConnectMessage) clientDecoder.readInbound())
                        .variableHeader().version()).isEqualTo(MqttVersion.MQTT_5.protocolLevel());
            } finally {
                connectPacket.release();
            }
            assertThat(brokerEncoder.writeOutbound(connect)).isTrue();
            ByteBuf brokerConnectPacket = brokerEncoder.readOutbound();
            brokerConnectPacket.release();

            MqttSubscriptionOption telemetryOption = new MqttSubscriptionOption(
                    MqttQoS.AT_LEAST_ONCE,
                    true,
                    true,
                    MqttSubscriptionOption.RetainedHandlingPolicy.DONT_SEND_AT_SUBSCRIBE);
            MqttSubscribeMessage subscribe = MqttMessageBuilders.subscribe()
                    .messageId(812)
                    .addSubscription("building/+/telemetry", telemetryOption)
                    .addSubscription(MqttQoS.AT_MOST_ONCE, "building/boiler-room/alerts")
                    .build();

            assertThat(clientEncoder.writeOutbound(subscribe)).isTrue();
            ByteBuf subscribePacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(subscribePacket.retain())).isTrue();
                MqttSubscribeMessage receivedSubscribe = brokerDecoder.readInbound();
                assertThat(receivedSubscribe.variableHeader().messageId()).isEqualTo(812);
                List<MqttTopicSubscription> subscriptions =
                        receivedSubscribe.payload().topicSubscriptions();
                assertThat(subscriptions).hasSize(2);
                assertThat(subscriptions.get(0).topicFilter()).isEqualTo("building/+/telemetry");
                assertThat(subscriptions.get(0).option()).isEqualTo(telemetryOption);
                assertThat(subscriptions.get(1).topicFilter()).isEqualTo("building/boiler-room/alerts");
                assertThat(subscriptions.get(1).qualityOfService()).isEqualTo(MqttQoS.AT_MOST_ONCE);

                MqttSubAckMessage acknowledgement = MqttMessageBuilders.subAck()
                        .packetId(receivedSubscribe.variableHeader().messageId())
                        .properties(new MqttProperties())
                        .addGrantedQoses(MqttQoS.AT_LEAST_ONCE, MqttQoS.FAILURE)
                        .build();
                assertThat(brokerEncoder.writeOutbound(acknowledgement)).isTrue();
                ByteBuf acknowledgementPacket = brokerEncoder.readOutbound();
                try {
                    assertThat(clientDecoder.writeInbound(acknowledgementPacket.retain())).isTrue();
                    MqttSubAckMessage receivedAcknowledgement = clientDecoder.readInbound();
                    assertThat(receivedAcknowledgement.variableHeader().messageId()).isEqualTo(812);
                    assertThat(receivedAcknowledgement.payload().grantedQoSLevels())
                            .containsExactly(MqttQoS.AT_LEAST_ONCE.value(), MqttQoS.FAILURE.value());
                } finally {
                    acknowledgementPacket.release();
                }
            } finally {
                subscribePacket.release();
            }
        } finally {
            clientEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            brokerEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }
}
