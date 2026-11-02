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
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT request-response subsystem: an application correlates a broker response
 * with a request using MQTT 5 response-topic and correlation-data properties.
 */
class MqttRequestResponseTest {

    @Test
    void correlatesResponseWithRequestMetadata() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel brokerEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("request-client")
                    .cleanSession(true)
                    .build();
            assertThat(clientEncoder.writeOutbound(connect)).isTrue();
            ByteBuf connectPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(connectPacket.retainedDuplicate())).isTrue();
                assertThat(((MqttConnectMessage) brokerDecoder.readInbound()).variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
                assertThat(clientDecoder.writeInbound(connectPacket.retainedDuplicate())).isTrue();
                assertThat(((MqttConnectMessage) clientDecoder.readInbound()).variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
            } finally {
                connectPacket.release();
            }
            assertThat(brokerEncoder.writeOutbound(connect)).isTrue();
            ByteBuf brokerConnectPacket = brokerEncoder.readOutbound();
            brokerConnectPacket.release();

            byte[] requestCorrelation = "request-42".getBytes(StandardCharsets.UTF_8);
            MqttProperties requestProperties = new MqttProperties();
            requestProperties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value(),
                    "building/gateway/responses"));
            requestProperties.add(new MqttProperties.BinaryProperty(
                    MqttProperties.MqttPropertyType.CORRELATION_DATA.value(), requestCorrelation));
            MqttPublishMessage request = MqttMessageBuilders.publish()
                    .topicName("building/gateway/requests")
                    .qos(MqttQoS.AT_LEAST_ONCE)
                    .messageId(42)
                    .properties(requestProperties)
                    .payload(Unpooled.copiedBuffer(
                            "{\"operation\":\"current-temperature\"}", StandardCharsets.UTF_8))
                    .build();

            assertThat(clientEncoder.writeOutbound(request)).isTrue();
            ByteBuf requestPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(requestPacket.retain())).isTrue();
                MqttPublishMessage receivedRequest = brokerDecoder.readInbound();
                try {
                    assertThat(receivedRequest.variableHeader().topicName())
                            .isEqualTo("building/gateway/requests");
                    assertThat(receivedRequest.payload().toString(StandardCharsets.UTF_8))
                            .isEqualTo("{\"operation\":\"current-temperature\"}");
                    assertThat(stringProperty(receivedRequest, MqttProperties.MqttPropertyType.RESPONSE_TOPIC))
                            .isEqualTo("building/gateway/responses");
                    assertThat(binaryProperty(receivedRequest,
                            MqttProperties.MqttPropertyType.CORRELATION_DATA))
                            .satisfies(value -> assertThat(Arrays.equals(value, requestCorrelation)).isTrue());

                    MqttProperties responseProperties = new MqttProperties();
                    responseProperties.add(new MqttProperties.BinaryProperty(
                            MqttProperties.MqttPropertyType.CORRELATION_DATA.value(), requestCorrelation));
                    MqttPublishMessage response = MqttMessageBuilders.publish()
                            .topicName("building/gateway/responses")
                            .qos(MqttQoS.AT_LEAST_ONCE)
                            .messageId(43)
                            .properties(responseProperties)
                            .payload(Unpooled.copiedBuffer("72.5", StandardCharsets.UTF_8))
                            .build();
                    assertThat(brokerEncoder.writeOutbound(response)).isTrue();
                    ByteBuf responsePacket = brokerEncoder.readOutbound();
                    try {
                        assertThat(clientDecoder.writeInbound(responsePacket.retain())).isTrue();
                        MqttPublishMessage receivedResponse = clientDecoder.readInbound();
                        try {
                            assertThat(receivedResponse.variableHeader().topicName())
                                    .isEqualTo("building/gateway/responses");
                            assertThat(receivedResponse.payload().toString(StandardCharsets.UTF_8))
                                    .isEqualTo("72.5");
                            assertThat(binaryProperty(receivedResponse,
                                    MqttProperties.MqttPropertyType.CORRELATION_DATA))
                                    .satisfies(value -> assertThat(
                                            Arrays.equals(value, requestCorrelation)).isTrue());
                        } finally {
                            receivedResponse.release();
                        }
                    } finally {
                        responsePacket.release();
                    }
                } finally {
                    receivedRequest.release();
                }
            } finally {
                requestPacket.release();
            }
        } finally {
            clientEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            brokerEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }

    private static String stringProperty(
            MqttPublishMessage message, MqttProperties.MqttPropertyType propertyType) {
        return message.variableHeader().properties().getProperties(propertyType.value()).get(0).value().toString();
    }

    private static byte[] binaryProperty(
            MqttPublishMessage message, MqttProperties.MqttPropertyType propertyType) {
        return (byte[]) message.variableHeader().properties().getProperties(propertyType.value()).get(0).value();
    }
}
