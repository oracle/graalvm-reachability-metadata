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
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MQTT enhanced authentication subsystem: an application performs a multi-step
 * challenge-response exchange while the MQTT 5 connection remains on the wire.
 */
class MqttEnhancedAuthenticationTest {

    @Test
    void completesChallengeResponseAuthentication() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        EmbeddedChannel brokerDecoder = new EmbeddedChannel(new MqttDecoder());
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new MqttDecoder());

        try {
            byte[] clientNonce = "client-nonce".getBytes(StandardCharsets.UTF_8);
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("analytics-client")
                    .cleanSession(true)
                    .keepAlive(30)
                    .properties(authenticationProperties("SCRAM-SHA-256", clientNonce))
                    .build();

            assertThat(clientEncoder.writeOutbound(connect)).isTrue();
            ByteBuf connectPacket = clientEncoder.readOutbound();
            try {
                assertThat(brokerDecoder.writeInbound(connectPacket.retainedDuplicate())).isTrue();
                MqttConnectMessage receivedConnect = brokerDecoder.readInbound();
                assertThat(receivedConnect.variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());
                assertAuthenticationProperties(receivedConnect.variableHeader().properties(), clientNonce);
                assertThat(clientDecoder.writeInbound(connectPacket.retainedDuplicate())).isTrue();
                assertThat(((MqttConnectMessage) clientDecoder.readInbound()).variableHeader().version())
                        .isEqualTo(MqttVersion.MQTT_5.protocolLevel());

                byte[] brokerNonce = "broker-nonce".getBytes(StandardCharsets.UTF_8);
                MqttMessage challenge = MqttMessageBuilders.auth()
                        .reasonCode(MqttReasonCodes.Auth.CONTINUE_AUTHENTICATION.byteValue())
                        .properties(authenticationProperties("SCRAM-SHA-256", brokerNonce))
                        .build();
                assertThat(clientEncoder.writeOutbound(challenge)).isTrue();
                ByteBuf challengePacket = clientEncoder.readOutbound();
                try {
                    assertThat(clientDecoder.writeInbound(challengePacket.retain())).isTrue();
                    MqttMessage receivedChallenge = clientDecoder.readInbound();
                    assertThat(receivedChallenge.decoderResult().isSuccess())
                            .withFailMessage("AUTH decode failed: %s", receivedChallenge.decoderResult())
                            .isTrue();
                    assertAuthenticationProperties(authHeader(receivedChallenge).properties(), brokerNonce);
                } finally {
                    challengePacket.release();
                }

                MqttMessage response = MqttMessageBuilders.auth()
                        .reasonCode(MqttReasonCodes.Auth.CONTINUE_AUTHENTICATION.byteValue())
                        .properties(authenticationProperties(
                                "SCRAM-SHA-256", "client-proof".getBytes(StandardCharsets.UTF_8)))
                        .build();
                assertThat(clientEncoder.writeOutbound(response)).isTrue();
                ByteBuf responsePacket = clientEncoder.readOutbound();
                try {
                    assertThat(brokerDecoder.writeInbound(responsePacket.retain())).isTrue();
                    MqttMessage receivedResponse = brokerDecoder.readInbound();
                    assertThat(authHeader(receivedResponse).reasonCode())
                            .isEqualTo(MqttReasonCodes.Auth.CONTINUE_AUTHENTICATION.byteValue());
                } finally {
                    responsePacket.release();
                }

                MqttMessage success = MqttMessageBuilders.auth()
                        .reasonCode(MqttReasonCodes.Auth.SUCCESS.byteValue())
                        .properties(authenticationProperties(
                                "SCRAM-SHA-256", "server-proof".getBytes(StandardCharsets.UTF_8)))
                        .build();
                assertThat(clientEncoder.writeOutbound(success)).isTrue();
                ByteBuf successPacket = clientEncoder.readOutbound();
                try {
                    assertThat(clientDecoder.writeInbound(successPacket.retain())).isTrue();
                    MqttMessage receivedSuccess = clientDecoder.readInbound();
                    assertThat(authHeader(receivedSuccess).reasonCode())
                            .isEqualTo(MqttReasonCodes.Auth.SUCCESS.byteValue());
                    assertAuthenticationProperties(
                            authHeader(receivedSuccess).properties(),
                            "server-proof".getBytes(StandardCharsets.UTF_8));
                } finally {
                    successPacket.release();
                }
            } finally {
                connectPacket.release();
            }
        } finally {
            clientEncoder.finishAndReleaseAll();
            brokerDecoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }

    private static MqttProperties authenticationProperties(String method, byte[] data) {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value(), method));
        properties.add(new MqttProperties.BinaryProperty(
                MqttProperties.MqttPropertyType.AUTHENTICATION_DATA.value(), data));
        return properties;
    }

    private static MqttReasonCodeAndPropertiesVariableHeader authHeader(MqttMessage message) {
        return (MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader();
    }

    private static void assertAuthenticationProperties(MqttProperties properties, byte[] expectedData) {
        assertThat(properties.getProperty(MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value()).value())
                .isEqualTo("SCRAM-SHA-256");
        assertThat(properties.getProperty(MqttProperties.MqttPropertyType.AUTHENTICATION_DATA.value()).value())
                .satisfies(value -> assertThat(Arrays.equals((byte[]) value, expectedData)).isTrue());
    }
}
