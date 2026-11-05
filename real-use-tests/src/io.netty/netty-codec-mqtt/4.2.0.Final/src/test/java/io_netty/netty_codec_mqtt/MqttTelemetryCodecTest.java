/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT publish codec subsystem: an application publishes JSON telemetry with MQTT
 * 5 properties, sends the encoded packet across a connection, and consumes the decoded message.
 *
 * The consumer decodes the payload with Jackson and uses the topic, delivery quality, message id,
 * and user properties to route the telemetry to the device record that an application would update.
 */
class MqttTelemetryCodecTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void publishesAndConsumesJsonTelemetryWithMqtt5Metadata() throws Exception {
        Telemetry telemetry = new Telemetry("boiler-room", 72.5, true);
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), "application/json"));
        properties.add(new MqttProperties.UserProperty("device", "thermostat-7"));

        ByteBuf payload = Unpooled.copiedBuffer(JSON.writeValueAsBytes(telemetry));
        MqttPublishMessage outboundMessage = MqttMessageBuilders.publish()
                .topicName("building/boiler-room/telemetry")
                .qos(MqttQoS.AT_LEAST_ONCE)
                .retained(true)
                .messageId(417)
                .properties(properties)
                .payload(payload)
                .build();
        EmbeddedChannel encoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel decoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("telemetry-gateway")
                    .cleanSession(true)
                    .keepAlive(30)
                    .build();
            assertThat(encoder.writeOutbound(connect)).isTrue();
            ByteBuf connectPacket = encoder.readOutbound();
            try {
                assertThat(decoder.writeInbound(connectPacket.retain())).isTrue();
                MqttConnectMessage receivedConnect = decoder.readInbound();
                assertThat(receivedConnect.variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
                assertThat(receivedConnect.payload().clientIdentifier()).isEqualTo("telemetry-gateway");
            } finally {
                connectPacket.release();
            }

            assertThat(encoder.writeOutbound(outboundMessage)).isTrue();
            ByteBuf wirePacket = encoder.readOutbound();
            try {
                assertThat(decoder.writeInbound(wirePacket.retain())).isTrue();
                MqttPublishMessage inboundMessage = decoder.readInbound();
                try {
                    Telemetry received = JSON.readValue(
                            inboundMessage.payload().toString(StandardCharsets.UTF_8), Telemetry.class);
                    assertThat(inboundMessage.variableHeader().topicName())
                            .isEqualTo("building/boiler-room/telemetry");
                    assertThat(inboundMessage.fixedHeader().qosLevel()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
                    assertThat(inboundMessage.fixedHeader().isRetain()).isTrue();
                    assertThat(inboundMessage.variableHeader().messageId()).isEqualTo(417);
                    assertThat(received).isEqualTo(telemetry);
                    assertThat(contentType(inboundMessage)).isEqualTo("application/json");
                    assertThat(userProperty(inboundMessage, "device")).isEqualTo("thermostat-7");
                } finally {
                    inboundMessage.release();
                }
            } finally {
                wirePacket.release();
            }
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    private static String contentType(MqttPublishMessage message) {
        List<? extends MqttProperties.MqttProperty> properties =
                message.variableHeader().properties().getProperties(
                        MqttProperties.MqttPropertyType.CONTENT_TYPE.value());
        return properties.get(0).value().toString();
    }

    private static String userProperty(MqttPublishMessage message, String key) {
        List<? extends MqttProperties.MqttProperty> properties =
                message.variableHeader().properties().getProperties(
                        MqttProperties.MqttPropertyType.USER_PROPERTY.value());
        MqttProperties.StringPair pair = (MqttProperties.StringPair) properties.get(0).value();
        assertThat(pair.key).isEqualTo(key);
        return pair.value;
    }

    private record Telemetry(String location, double temperature, boolean alarm) {
    }
}
