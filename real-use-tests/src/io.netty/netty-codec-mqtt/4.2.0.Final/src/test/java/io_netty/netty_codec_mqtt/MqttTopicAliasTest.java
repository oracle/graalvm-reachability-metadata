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
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT topic alias subsystem: a device sends a full topic once and then uses the
 * MQTT 5 alias on a compact telemetry packet.
 */
class MqttTopicAliasTest {

    @Test
    void reusesTopicAliasForSubsequentTelemetry() {
        EmbeddedChannel deviceEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        Map<Integer, String> topicAliases = new HashMap<>();

        try {
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("compact-thermostat")
                    .cleanSession(true)
                    .build();
            assertThat(deviceEncoder.writeOutbound(connect)).isTrue();
            ByteBuf connectPacket = deviceEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(connectPacket.retain())).isTrue();
                MqttConnectMessage receivedConnect = brokerDecoder.readInbound();
                assertThat(receivedConnect.variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
            } finally {
                connectPacket.release();
            }

            MqttPublishMessage firstTelemetry = publish(
                    "building/boiler-room/temperature", 72.5);
            assertThat(deviceEncoder.writeOutbound(firstTelemetry)).isTrue();
            ByteBuf firstPacket = deviceEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(firstPacket.retain())).isTrue();
                MqttPublishMessage receivedFirst = brokerDecoder.readInbound();
                try {
                    assertThat(receivedFirst.variableHeader().topicName())
                            .isEqualTo("building/boiler-room/temperature");
                    assertThat(topicAlias(receivedFirst)).isEqualTo(7);
                    topicAliases.put(topicAlias(receivedFirst), receivedFirst.variableHeader().topicName());
                    assertThat(receivedFirst.payload().toString(StandardCharsets.UTF_8))
                            .isEqualTo("72.5");
                } finally {
                    receivedFirst.release();
                }
            } finally {
                firstPacket.release();
            }

            MqttPublishMessage compactTelemetry = publish("", 73.0);
            assertThat(deviceEncoder.writeOutbound(compactTelemetry)).isTrue();
            ByteBuf compactPacket = deviceEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(compactPacket.retain())).isTrue();
                MqttPublishMessage receivedCompact = brokerDecoder.readInbound();
                try {
                    assertThat(receivedCompact.variableHeader().topicName()).isEmpty();
                    int alias = topicAlias(receivedCompact);
                    assertThat(topicAliases).containsEntry(alias, "building/boiler-room/temperature");
                    assertThat(receivedCompact.payload().toString(StandardCharsets.UTF_8))
                            .isEqualTo("73.0");
                } finally {
                    receivedCompact.release();
                }
            } finally {
                compactPacket.release();
            }
        } finally {
            deviceEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
        }
    }

    private static MqttPublishMessage publish(String topic, double temperature) {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.TOPIC_ALIAS.value(), 7));
        return MqttMessageBuilders.publish()
                .topicName(topic)
                .qos(MqttQoS.AT_MOST_ONCE)
                .properties(properties)
                .payload(Unpooled.copiedBuffer(
                        Double.toString(temperature), StandardCharsets.UTF_8))
                .build();
    }

    private static int topicAlias(MqttPublishMessage message) {
        return (Integer) message.variableHeader().properties()
                .getProperty(MqttProperties.MqttPropertyType.TOPIC_ALIAS.value()).value();
    }
}
