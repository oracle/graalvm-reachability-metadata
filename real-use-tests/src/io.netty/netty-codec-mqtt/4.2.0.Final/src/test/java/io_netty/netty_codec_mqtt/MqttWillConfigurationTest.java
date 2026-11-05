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
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT last-will configuration subsystem: an application configures a retained
 * offline event with MQTT 5 expiry, content, and correlation metadata when it connects.
 */
class MqttWillConfigurationTest {

    @Test
    void encodesLastWillPropertiesForBroker() {
        EmbeddedChannel deviceEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        byte[] correlationData = "thermostat-7".getBytes(StandardCharsets.UTF_8);

        try {
            MqttProperties willProperties = new MqttProperties();
            willProperties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.WILL_DELAY_INTERVAL.value(), 30));
            willProperties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value(), 600));
            willProperties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value(), 1));
            willProperties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), "application/json"));
            willProperties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value(),
                    "building/boiler-room/status/ack"));
            willProperties.add(new MqttProperties.BinaryProperty(
                    MqttProperties.MqttPropertyType.CORRELATION_DATA.value(), correlationData));

            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("thermostat-7")
                    .cleanSession(false)
                    .keepAlive(30)
                    .willFlag(true)
                    .willQoS(MqttQoS.AT_LEAST_ONCE)
                    .willRetain(true)
                    .willTopic("building/boiler-room/status")
                    .willMessage("{\"online\":false}")
                    .willProperties(willProperties)
                    .build();

            assertThat(deviceEncoder.writeOutbound(connect)).isTrue();
            ByteBuf packet = deviceEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(packet.retain())).isTrue();
                MqttConnectMessage received = brokerDecoder.readInbound();
                assertThat(received.variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
                assertThat(received.variableHeader().isCleanSession()).isFalse();
                assertThat(received.payload().clientIdentifier()).isEqualTo("thermostat-7");
                assertThat(received.payload().willTopic()).isEqualTo("building/boiler-room/status");
                assertThat(received.payload().willMessage()).isEqualTo("{\"online\":false}");
                assertThat(received.variableHeader().willQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE.value());
                assertThat(received.variableHeader().isWillRetain()).isTrue();

                MqttProperties decoded = received.payload().willProperties();
                assertThat(integerProperty(decoded,
                        MqttProperties.MqttPropertyType.WILL_DELAY_INTERVAL)).isEqualTo(30);
                assertThat(integerProperty(decoded,
                        MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL)).isEqualTo(600);
                assertThat(integerProperty(decoded,
                        MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR)).isEqualTo(1);
                assertThat(stringProperty(decoded, MqttProperties.MqttPropertyType.CONTENT_TYPE))
                        .isEqualTo("application/json");
                assertThat(stringProperty(decoded, MqttProperties.MqttPropertyType.RESPONSE_TOPIC))
                        .isEqualTo("building/boiler-room/status/ack");
                assertThat(binaryProperty(decoded, MqttProperties.MqttPropertyType.CORRELATION_DATA))
                        .satisfies(value -> assertThat(Arrays.equals(value, correlationData)).isTrue());
            } finally {
                packet.release();
            }
        } finally {
            deviceEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
        }
    }

    private static int integerProperty(
            MqttProperties properties, MqttProperties.MqttPropertyType propertyType) {
        return (Integer) properties.getProperty(propertyType.value()).value();
    }

    private static String stringProperty(
            MqttProperties properties, MqttProperties.MqttPropertyType propertyType) {
        return properties.getProperty(propertyType.value()).value().toString();
    }

    private static byte[] binaryProperty(
            MqttProperties properties, MqttProperties.MqttPropertyType propertyType) {
        return (byte[]) properties.getProperty(propertyType.value()).value();
    }
}
