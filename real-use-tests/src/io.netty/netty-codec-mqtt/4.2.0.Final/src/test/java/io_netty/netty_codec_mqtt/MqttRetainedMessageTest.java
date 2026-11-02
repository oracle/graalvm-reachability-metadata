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
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT retained-message subsystem: a broker stores an online state and delivers it
 * to a subscriber when the subscriber first joins the topic.
 */
class MqttRetainedMessageTest {

    @Test
    void deliversRetainedStateWhenSubscriberJoins() {
        EmbeddedChannel publisherEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel subscriberEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel subscriberDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttConnectMessage publisherConnect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("status-publisher")
                    .cleanSession(true)
                    .build();
            assertThat(publisherEncoder.writeOutbound(publisherConnect)).isTrue();
            ByteBuf publisherConnectPacket = publisherEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(publisherConnectPacket.retainedDuplicate())).isTrue();
                MqttConnectMessage receivedPublisherConnect = brokerDecoder.readInbound();
                assertThat(receivedPublisherConnect.variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
            } finally {
                publisherConnectPacket.release();
            }

            MqttConnectMessage subscriberConnect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("status-dashboard")
                    .cleanSession(true)
                    .build();
            assertThat(subscriberEncoder.writeOutbound(subscriberConnect)).isTrue();
            ByteBuf subscriberConnectPacket = subscriberEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(subscriberConnectPacket.retainedDuplicate())).isTrue();
                MqttConnectMessage receivedSubscriberConnect = brokerDecoder.readInbound();
                assertThat(receivedSubscriberConnect.variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
            } finally {
                subscriberConnectPacket.release();
            }

            MqttPublishMessage retainedState = MqttMessageBuilders.publish()
                    .topicName("building/boiler-room/status")
                    .retained(true)
                    .qos(MqttQoS.AT_LEAST_ONCE)
                    .messageId(17)
                    .payload(Unpooled.copiedBuffer("online", StandardCharsets.UTF_8))
                    .build();
            assertThat(publisherEncoder.writeOutbound(retainedState)).isTrue();
            ByteBuf retainedStatePacket = publisherEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(retainedStatePacket.retainedDuplicate())).isTrue();
                MqttPublishMessage receivedState = brokerDecoder.readInbound();
                try {
                    assertThat(receivedState.fixedHeader().isRetain()).isTrue();
                    assertThat(receivedState.variableHeader().topicName())
                            .isEqualTo("building/boiler-room/status");
                    assertThat(receivedState.payload().toString(StandardCharsets.UTF_8))
                            .isEqualTo("online");
                } finally {
                    receivedState.release();
                }
            } finally {
                retainedStatePacket.release();
            }

            MqttSubscriptionOption subscriptionOption = new MqttSubscriptionOption(
                    MqttQoS.AT_LEAST_ONCE,
                    false,
                    true,
                    MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE);
            MqttSubscribeMessage subscription = MqttMessageBuilders.subscribe()
                    .messageId(18)
                    .addSubscription("building/boiler-room/status", subscriptionOption)
                    .build();
            assertThat(subscriberEncoder.writeOutbound(subscription)).isTrue();
            ByteBuf subscriptionPacket = subscriberEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(subscriptionPacket.retainedDuplicate())).isTrue();
                MqttSubscribeMessage receivedSubscription = brokerDecoder.readInbound();
                assertThat(receivedSubscription.payload().topicSubscriptions())
                        .singleElement()
                        .satisfies(topicSubscription -> {
                            assertThat(topicSubscription.topicFilter())
                                    .isEqualTo("building/boiler-room/status");
                            assertThat(topicSubscription.option()).isEqualTo(subscriptionOption);
                        });
            } finally {
                subscriptionPacket.release();
            }

            MqttPublishMessage retainedDelivery = MqttMessageBuilders.publish()
                    .topicName("building/boiler-room/status")
                    .retained(true)
                    .qos(MqttQoS.AT_LEAST_ONCE)
                    .messageId(19)
                    .payload(Unpooled.copiedBuffer("online", StandardCharsets.UTF_8))
                    .build();
            assertThat(brokerEncoder.writeOutbound(retainedDelivery)).isTrue();
            ByteBuf retainedDeliveryPacket = brokerEncoder.readOutbound();
            try {
                assertThat(subscriberDecoder.writeInbound(retainedDeliveryPacket.retainedDuplicate()))
                        .isTrue();
                MqttPublishMessage receivedDelivery = subscriberDecoder.readInbound();
                try {
                    assertThat(receivedDelivery.fixedHeader().isRetain()).isTrue();
                    assertThat(receivedDelivery.variableHeader().topicName())
                            .isEqualTo("building/boiler-room/status");
                    assertThat(receivedDelivery.payload().toString(StandardCharsets.UTF_8))
                            .isEqualTo("online");
                } finally {
                    receivedDelivery.release();
                }
            } finally {
                retainedDeliveryPacket.release();
            }
        } finally {
            publisherEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            subscriberEncoder.finishAndReleaseAll();
            brokerEncoder.finishAndReleaseAll();
            subscriberDecoder.finishAndReleaseAll();
        }
    }
}
