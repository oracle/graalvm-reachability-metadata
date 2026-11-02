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
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT 3.1.1 compatibility subsystem: a legacy device connects to a broker and
 * publishes a telemetry reading without MQTT 5 properties.
 */
class MqttV311CompatibilityTest {

    @Test
    void connectsAndPublishesWithMqtt311() {
        EmbeddedChannel deviceEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel deviceDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_3_1_1)
                    .clientId("legacy-thermostat")
                    .cleanSession(true)
                    .keepAlive(20)
                    .build();
            assertThat(deviceEncoder.writeOutbound(connect)).isTrue();
            ByteBuf connectPacket = deviceEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(connectPacket.retain())).isTrue();
                MqttConnectMessage receivedConnect = brokerDecoder.readInbound();
                assertThat(receivedConnect.variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_3_1_1.protocolLevel());
                assertThat(receivedConnect.payload().clientIdentifier())
                        .isEqualTo("legacy-thermostat");
                assertThat(receivedConnect.variableHeader().isCleanSession()).isTrue();
                assertThat(receivedConnect.variableHeader().keepAliveTimeSeconds()).isEqualTo(20);
            } finally {
                connectPacket.release();
            }

            MqttConnAckMessage connAck = MqttMessageBuilders.connAck()
                    .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                    .sessionPresent(false)
                    .properties(new MqttProperties())
                    .build();
            assertThat(brokerEncoder.writeOutbound(connAck)).isTrue();
            ByteBuf connAckPacket = brokerEncoder.readOutbound();
            try {
                assertThat(deviceDecoder.writeInbound(connAckPacket.retain())).isTrue();
                MqttConnAckMessage receivedConnAck = deviceDecoder.readInbound();
                assertThat(receivedConnAck.variableHeader().connectReturnCode())
                        .isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED);
                assertThat(receivedConnAck.variableHeader().isSessionPresent()).isFalse();
            } finally {
                connAckPacket.release();
            }

            MqttPublishMessage telemetry = MqttMessageBuilders.publish()
                    .topicName("building/boiler-room/temperature")
                    .qos(MqttQoS.AT_MOST_ONCE)
                    .properties(new MqttProperties())
                    .payload(Unpooled.copiedBuffer("72.5", StandardCharsets.UTF_8))
                    .build();
            assertThat(deviceEncoder.writeOutbound(telemetry)).isTrue();
            ByteBuf telemetryPacket = deviceEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(telemetryPacket.retain())).isTrue();
                MqttPublishMessage receivedTelemetry = brokerDecoder.readInbound();
                try {
                    assertThat(receivedTelemetry.fixedHeader().messageType())
                            .isEqualTo(MqttMessageType.PUBLISH);
                    assertThat(receivedTelemetry.fixedHeader().qosLevel())
                            .isEqualTo(MqttQoS.AT_MOST_ONCE);
                    assertThat(receivedTelemetry.variableHeader().topicName())
                            .isEqualTo("building/boiler-room/temperature");
                    assertThat(receivedTelemetry.variableHeader().messageId()).isEqualTo(-1);
                    assertThat(receivedTelemetry.payload().toString(StandardCharsets.UTF_8))
                            .isEqualTo("72.5");
                    assertThat(receivedTelemetry.variableHeader().properties().isEmpty()).isTrue();
                } finally {
                    receivedTelemetry.release();
                }
            } finally {
                telemetryPacket.release();
            }
        } finally {
            deviceEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            brokerEncoder.finishAndReleaseAll();
            deviceDecoder.finishAndReleaseAll();
        }
    }
}
