/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttSubscribePayload;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnacceptableProtocolVersionException;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubAckPayload;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribePayload;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Netty_codec_mqttTest {
    private static final Charset UTF_8 = CharsetUtil.UTF_8;

    @Test
    void connectBuilderSetsFlagsPayloadAndEncodesExpectedMqtt311WireFormat() {
        MqttConnectMessage connect = MqttMessageBuilders.connect()
                .protocolVersion(MqttVersion.MQTT_3_1_1)
                .clientId("client-1")
                .cleanSession(true)
                .keepAlive(30)
                .willFlag(true)
                .willQoS(MqttQoS.AT_LEAST_ONCE)
                .willRetain(true)
                .willTopic("alerts/status")
                .willMessage("offline")
                .hasUser(true)
                .username("guest")
                .hasPassword(true)
                .password("secret")
                .build();

        MqttConnectVariableHeader variableHeader = connect.variableHeader();
        MqttConnectPayload payload = connect.payload();
        assertThat(connect.fixedHeader().messageType()).isEqualTo(MqttMessageType.CONNECT);
        assertThat(variableHeader.name()).isEqualTo("MQTT");
        assertThat(variableHeader.version()).isEqualTo(4);
        assertThat(variableHeader.isCleanSession()).isTrue();
        assertThat(variableHeader.isWillFlag()).isTrue();
        assertThat(variableHeader.willQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE.value());
        assertThat(variableHeader.isWillRetain()).isTrue();
        assertThat(variableHeader.hasUserName()).isTrue();
        assertThat(variableHeader.hasPassword()).isTrue();
        assertThat(variableHeader.keepAliveTimeSeconds()).isEqualTo(30);
        assertThat(payload.clientIdentifier()).isEqualTo("client-1");
        assertThat(payload.willTopic()).isEqualTo("alerts/status");
        assertThat(payload.willMessage()).isEqualTo("offline");
        assertThat(payload.userName()).isEqualTo("guest");
        assertThat(payload.password()).isEqualTo("secret");

        ByteBuf encoded = encode(connect);
        try {
            assertThat(ByteBufUtil.hexDump(encoded)).isEqualTo(
                    "103b00044d51545404ee001e0008636c69656e742d31000d616c657274732f737461747573"
                            + "00076f66666c696e65000567756573740006736563726574");
            MqttConnectMessage decoded = (MqttConnectMessage) decode(encoded.retainedDuplicate());
            assertThat(decoded.variableHeader().name()).isEqualTo("MQTT");
            assertThat(decoded.variableHeader().version()).isEqualTo(4);
            assertThat(decoded.payload().clientIdentifier()).isEqualTo("client-1");
            assertThat(decoded.payload().willTopic()).isEqualTo("alerts/status");
            assertThat(decoded.payload().willMessage()).isEqualTo("offline");
            assertThat(decoded.payload().userName()).isEqualTo("guest");
            assertThat(decoded.payload().password()).isEqualTo("secret");
        } finally {
            encoded.release();
        }
    }

    @Test
    void mqtt5ConnectMessageCarriesPropertiesAndBinaryCredentials() {
        MqttProperties connectProperties = new MqttProperties();
        connectProperties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), 60));
        connectProperties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value(), "token"));
        MqttProperties willProperties = new MqttProperties();
        willProperties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), "text/plain"));
        byte[] willMessage = "offline".getBytes(UTF_8);
        byte[] password = new byte[] {1, 2, 3, 4};

        MqttConnectMessage connect = MqttMessageBuilders.connect()
                .protocolVersion(MqttVersion.MQTT_5)
                .clientId("mqtt5-client")
                .cleanSession(false)
                .keepAlive(120)
                .willFlag(true)
                .willQoS(MqttQoS.EXACTLY_ONCE)
                .willTopic("status/client")
                .willMessage(willMessage)
                .willProperties(willProperties)
                .hasUser(true)
                .username("device-user")
                .hasPassword(true)
                .password(password)
                .properties(connectProperties)
                .build();

        assertThat(connect.variableHeader().version()).isEqualTo(5);
        assertThat(connect.variableHeader().isCleanSession()).isFalse();
        assertThat(connect.variableHeader().properties().getProperty(
                MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value()).value()).isEqualTo(60);
        assertThat(connect.variableHeader().properties().getProperty(
                MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value()).value()).isEqualTo("token");
        assertThat(connect.payload().willProperties().getProperty(
                MqttProperties.MqttPropertyType.CONTENT_TYPE.value()).value()).isEqualTo("text/plain");
        assertThat(connect.payload().willMessageInBytes()).containsExactly(willMessage);
        assertThat(connect.payload().passwordInBytes()).containsExactly(password);
    }

    @Test
    void propertiesSupportSingleLookupMultipleUserPropertiesAndEmptySingleton() {
        MqttProperties properties = new MqttProperties();
        MqttProperties.UserProperties userProperties = new MqttProperties.UserProperties();
        userProperties.add("tenant", "alpha");
        userProperties.add(new MqttProperties.StringPair("trace", "42"));
        properties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), "application/json"));
        properties.add(new MqttProperties.BinaryProperty(
                MqttProperties.MqttPropertyType.CORRELATION_DATA.value(), new byte[] {9, 8, 7}));
        properties.add(new MqttProperties.UserProperty("source", "sensor-a"));
        properties.add(userProperties);

        assertThat(properties.isEmpty()).isFalse();
        assertThat(properties.listAll()).hasSize(3);
        assertThat(properties.getProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value()).value())
                .isEqualTo("application/json");
        assertThat((byte[]) properties.getProperty(
                MqttProperties.MqttPropertyType.CORRELATION_DATA.value()).value())
                .containsExactly((byte) 9, (byte) 8, (byte) 7);
        assertThat(properties.getProperties(MqttProperties.MqttPropertyType.USER_PROPERTY.value())).hasSize(3);
        assertThat(properties.getProperty(MqttProperties.MqttPropertyType.USER_PROPERTY.value()).value())
                .isEqualTo(Arrays.asList(
                        new MqttProperties.StringPair("source", "sensor-a"),
                        new MqttProperties.StringPair("tenant", "alpha"),
                        new MqttProperties.StringPair("trace", "42")));
        assertThat(userProperties.value()).containsExactly(
                new MqttProperties.StringPair("tenant", "alpha"),
                new MqttProperties.StringPair("trace", "42"));
        assertThat(MqttProperties.NO_PROPERTIES.isEmpty()).isTrue();
    }

    @Test
    void publishMessageRoundTripsPayloadHeadersAndReferenceCountOperations() {
        ByteBuf payload = Unpooled.copiedBuffer("22.5\u00b0C", UTF_8);
        MqttPublishMessage publish = MqttMessageBuilders.publish()
                .topicName("sensors/temperature")
                .messageId(1234)
                .qos(MqttQoS.AT_LEAST_ONCE)
                .retained(true)
                .payload(payload)
                .build();

        assertThat(publish.fixedHeader().messageType()).isEqualTo(MqttMessageType.PUBLISH);
        assertThat(publish.fixedHeader().qosLevel()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(publish.fixedHeader().isRetain()).isTrue();
        assertThat(publish.variableHeader().topicName()).isEqualTo("sensors/temperature");
        assertThat(publish.variableHeader().packetId()).isEqualTo(1234);
        assertThat(publish.content().toString(UTF_8)).isEqualTo("22.5\u00b0C");

        MqttPublishMessage copy = publish.copy();
        MqttPublishMessage duplicate = publish.duplicate();
        MqttPublishMessage replacement = publish.replace(Unpooled.copiedBuffer("23.0\u00b0C", UTF_8));
        try {
            publish.content().setByte(0, '3');
            assertThat(copy.payload().toString(UTF_8)).isEqualTo("22.5\u00b0C");
            assertThat(duplicate.payload().toString(UTF_8)).isEqualTo("32.5\u00b0C");
            assertThat(replacement.variableHeader().topicName()).isEqualTo("sensors/temperature");
            assertThat(replacement.payload().toString(UTF_8)).isEqualTo("23.0\u00b0C");
            assertThat(publish.retain()).isSameAs(publish);
            assertThat(publish.refCnt()).isEqualTo(2);
        } finally {
            copy.release();
            duplicate.release();
            replacement.release();
            publish.release();
        }

        MqttPublishMessage outboundPublish = MqttMessageBuilders.publish()
                .topicName("sensors/temperature")
                .messageId(1234)
                .qos(MqttQoS.AT_LEAST_ONCE)
                .retained(true)
                .payload(Unpooled.copiedBuffer("22.5\u00b0C", UTF_8))
                .build();
        ByteBuf encoded = encode(outboundPublish);
        try {
            assertThat(ByteBufUtil.hexDump(encoded)).isEqualTo(
                    "331e001373656e736f72732f74656d706572617475726504d232322e35c2b043");
            MqttPublishMessage decoded = (MqttPublishMessage) decode(encoded.retainedDuplicate());
            try {
                assertThat(decoded.fixedHeader().qosLevel()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
                assertThat(decoded.fixedHeader().isRetain()).isTrue();
                assertThat(decoded.variableHeader().topicName()).isEqualTo("sensors/temperature");
                assertThat(decoded.variableHeader().packetId()).isEqualTo(1234);
                assertThat(decoded.payload().toString(UTF_8)).isEqualTo("22.5\u00b0C");
                assertThat(decoded.decoderResult()).isEqualTo(DecoderResult.SUCCESS);
            } finally {
                decoded.release();
            }
        } finally {
            encoded.release();
        }
    }

    @Test
    void mqtt5PublishPropertiesRoundTripAfterProtocolVersionNegotiation() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value(), 1));
        properties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), "application/json"));
        properties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value(), "devices/replies"));
        properties.add(new MqttProperties.BinaryProperty(
                MqttProperties.MqttPropertyType.CORRELATION_DATA.value(), new byte[] {1, 3, 5}));
        properties.add(new MqttProperties.UserProperty("source", "thermostat"));
        MqttPublishMessage publish = MqttMessageBuilders.publish()
                .topicName("devices/1/telemetry")
                .messageId(314)
                .qos(MqttQoS.AT_LEAST_ONCE)
                .properties(properties)
                .payload(Unpooled.copiedBuffer("{\"temp\":22}", UTF_8))
                .build();

        MqttPublishMessage decoded = (MqttPublishMessage) encodeAndDecodeAfterMqtt5Connect(publish);
        try {
            assertThat(decoded.fixedHeader().messageType()).isEqualTo(MqttMessageType.PUBLISH);
            assertThat(decoded.fixedHeader().qosLevel()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
            assertThat(decoded.variableHeader().topicName()).isEqualTo("devices/1/telemetry");
            assertThat(decoded.variableHeader().packetId()).isEqualTo(314);
            assertThat(decoded.content().toString(UTF_8)).isEqualTo("{\"temp\":22}");
            MqttProperties decodedProperties = decoded.variableHeader().properties();
            assertThat(decodedProperties.getProperty(
                    MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value()).value()).isEqualTo(1);
            assertThat(decodedProperties.getProperty(
                    MqttProperties.MqttPropertyType.CONTENT_TYPE.value()).value()).isEqualTo("application/json");
            assertThat(decodedProperties.getProperty(
                    MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value()).value()).isEqualTo("devices/replies");
            assertThat((byte[]) decodedProperties.getProperty(
                    MqttProperties.MqttPropertyType.CORRELATION_DATA.value()).value())
                    .containsExactly((byte) 1, (byte) 3, (byte) 5);
            assertThat(decodedProperties.getProperty(MqttProperties.MqttPropertyType.USER_PROPERTY.value()).value())
                    .isEqualTo(Arrays.asList(new MqttProperties.StringPair("source", "thermostat")));
        } finally {
            decoded.release();
        }
    }

    @Test
    void subscribeBuilderRoundTripsMultipleSubscriptionsAndSubscriptionOptions() {
        MqttSubscriptionOption retainedOption = new MqttSubscriptionOption(
                MqttQoS.EXACTLY_ONCE,
                true,
                true,
                MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS);
        MqttSubscribeMessage subscribe = MqttMessageBuilders.subscribe()
                .messageId(7)
                .addSubscription(MqttQoS.AT_MOST_ONCE, "devices/+/status")
                .addSubscription("alerts/#", retainedOption)
                .build();

        assertThat(subscribe.fixedHeader().messageType()).isEqualTo(MqttMessageType.SUBSCRIBE);
        assertThat(subscribe.fixedHeader().qosLevel()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(subscribe.variableHeader().messageId()).isEqualTo(7);
        assertThat(subscribe.payload().topicSubscriptions()).hasSize(2);
        assertThat(subscribe.payload().topicSubscriptions().get(1).option()).isEqualTo(retainedOption);

        MqttSubscribeMessage decoded = (MqttSubscribeMessage) encodeAndDecode(subscribe);
        List<MqttTopicSubscription> subscriptions = decoded.payload().topicSubscriptions();
        assertThat(decoded.variableHeader().messageId()).isEqualTo(7);
        assertThat(subscriptions).hasSize(2);
        assertThat(subscriptions.get(0).topicName()).isEqualTo("devices/+/status");
        assertThat(subscriptions.get(0).qualityOfService()).isEqualTo(MqttQoS.AT_MOST_ONCE);
        assertThat(subscriptions.get(1).topicName()).isEqualTo("alerts/#");
        assertThat(subscriptions.get(1).qualityOfService()).isEqualTo(MqttQoS.EXACTLY_ONCE);
        assertThat(subscriptions.get(1).option().isNoLocal()).isFalse();
        assertThat(subscriptions.get(1).option().isRetainAsPublished()).isFalse();
        assertThat(subscriptions.get(1).option().retainHandling())
                .isEqualTo(MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE);
    }

    @Test
    void mqtt5SubscribePropertiesAndSubscriptionOptionsRoundTripAfterProtocolVersionNegotiation() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value(), 42));
        properties.add(new MqttProperties.UserProperty("subscription", "audit"));
        MqttSubscriptionOption retainedOption = new MqttSubscriptionOption(
                MqttQoS.EXACTLY_ONCE,
                true,
                true,
                MqttSubscriptionOption.RetainedHandlingPolicy.DONT_SEND_AT_SUBSCRIBE);
        MqttSubscribeMessage subscribe = MqttMessageBuilders.subscribe()
                .messageId(77)
                .properties(properties)
                .addSubscription("devices/+/events", retainedOption)
                .build();

        MqttSubscribeMessage decoded = (MqttSubscribeMessage) encodeAndDecodeAfterMqtt5Connect(subscribe);
        MqttMessageIdAndPropertiesVariableHeader decodedVariableHeader = decoded.idAndPropertiesVariableHeader();
        MqttProperties decodedProperties = decodedVariableHeader.properties();
        MqttTopicSubscription decodedSubscription = decoded.payload().topicSubscriptions().get(0);
        MqttSubscriptionOption decodedOption = decodedSubscription.option();

        assertThat(decodedVariableHeader.messageId()).isEqualTo(77);
        assertThat(decodedProperties.getProperty(
                MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value()).value()).isEqualTo(42);
        assertThat(decodedProperties.getProperty(MqttProperties.MqttPropertyType.USER_PROPERTY.value()).value())
                .isEqualTo(Arrays.asList(new MqttProperties.StringPair("subscription", "audit")));
        assertThat(decoded.payload().topicSubscriptions()).hasSize(1);
        assertThat(decodedSubscription.topicName()).isEqualTo("devices/+/events");
        assertThat(decodedOption.qos()).isEqualTo(MqttQoS.EXACTLY_ONCE);
        assertThat(decodedOption.isNoLocal()).isTrue();
        assertThat(decodedOption.isRetainAsPublished()).isTrue();
        assertThat(decodedOption.retainHandling())
                .isEqualTo(MqttSubscriptionOption.RetainedHandlingPolicy.DONT_SEND_AT_SUBSCRIBE);
    }

    @Test
    void unsubscribeBuilderRoundTripsTopicFilters() {
        MqttUnsubscribeMessage unsubscribe = MqttMessageBuilders.unsubscribe()
                .messageId(99)
                .addTopicFilter("devices/+/status")
                .addTopicFilter("alerts/#")
                .build();

        assertThat(unsubscribe.fixedHeader().messageType()).isEqualTo(MqttMessageType.UNSUBSCRIBE);
        assertThat(unsubscribe.fixedHeader().qosLevel()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(unsubscribe.variableHeader().messageId()).isEqualTo(99);
        assertThat(unsubscribe.payload().topics()).containsExactly("devices/+/status", "alerts/#");

        MqttUnsubscribeMessage decoded = (MqttUnsubscribeMessage) encodeAndDecode(unsubscribe);
        assertThat(decoded.variableHeader().messageId()).isEqualTo(99);
        assertThat(decoded.payload().topics()).containsExactly("devices/+/status", "alerts/#");
    }

    @Test
    void acknowledgementBuildersExposeReasonCodesPropertiesAndRoundTrip() {
        MqttProperties ackProperties = new MqttProperties();
        ackProperties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.REASON_STRING.value(), "accepted"));
        MqttConnAckMessage connAck = MqttMessageBuilders.connAck()
                .sessionPresent(true)
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .properties(ackProperties)
                .build();
        MqttSubAckMessage subAck = MqttMessageBuilders.subAck()
                .packetId(7)
                .addGrantedQoses(MqttQoS.AT_MOST_ONCE, MqttQoS.EXACTLY_ONCE, MqttQoS.FAILURE)
                .build();
        MqttUnsubAckMessage unsubAck = MqttMessageBuilders.unsubAck()
                .packetId(99)
                .addReasonCodes((short) 0, (short) 17)
                .build();
        MqttMessage pubAck = MqttMessageBuilders.pubAck()
                .packetId(1234)
                .reasonCode((byte) 0)
                .properties(ackProperties)
                .build();

        assertThat(connAck.variableHeader().isSessionPresent()).isTrue();
        assertThat(connAck.variableHeader().connectReturnCode()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED);
        assertThat(connAck.variableHeader().properties().getProperty(
                MqttProperties.MqttPropertyType.REASON_STRING.value()).value()).isEqualTo("accepted");
        assertThat(subAck.payload().grantedQoSLevels()).containsExactly(0, 2, 128);
        assertThat(subAck.payload().reasonCodes()).containsExactly(0, 2, 128);
        assertThat(unsubAck.payload().unsubscribeReasonCodes()).containsExactly((short) 0, (short) 17);
        MqttPubReplyMessageVariableHeader pubAckVariableHeader =
                (MqttPubReplyMessageVariableHeader) pubAck.variableHeader();
        assertThat(pubAckVariableHeader.messageId()).isEqualTo(1234);
        assertThat(pubAckVariableHeader.reasonCode()).isZero();
        assertThat(pubAckVariableHeader.properties().getProperty(
                MqttProperties.MqttPropertyType.REASON_STRING.value()).value()).isEqualTo("accepted");

        MqttConnAckMessage decodedConnAck = (MqttConnAckMessage) encodeAndDecode(connAck);
        MqttSubAckMessage decodedSubAck = (MqttSubAckMessage) encodeAndDecode(subAck);
        MqttUnsubAckMessage decodedUnsubAck = (MqttUnsubAckMessage) encodeAndDecode(unsubAck);
        assertThat(decodedConnAck.variableHeader().connectReturnCode())
                .isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED);
        assertThat(decodedConnAck.variableHeader().isSessionPresent()).isTrue();
        assertThat(decodedSubAck.variableHeader().messageId()).isEqualTo(7);
        assertThat(decodedSubAck.payload().grantedQoSLevels()).containsExactly(0, 2, 128);
        assertThat(decodedUnsubAck.variableHeader().messageId()).isEqualTo(99);
        assertThat(decodedUnsubAck.payload().unsubscribeReasonCodes()).containsExactly((short) 0, (short) 17);
    }

    @Test
    void messageFactoryCreatesTypedMessagesFromPublicHeadersAndPayloads() {
        MqttFixedHeader publishFixedHeader = new MqttFixedHeader(
                MqttMessageType.PUBLISH, true, MqttQoS.EXACTLY_ONCE, false, 0);
        MqttPublishVariableHeader publishVariableHeader = new MqttPublishVariableHeader(
                "events/audit", 321, MqttProperties.NO_PROPERTIES);
        ByteBuf publishPayload = Unpooled.copiedBuffer("created", UTF_8);
        MqttMessage publish = MqttMessageFactory.newMessage(publishFixedHeader, publishVariableHeader, publishPayload);

        try {
            assertThat(publish).isInstanceOf(MqttPublishMessage.class);
            MqttPublishMessage publishMessage = (MqttPublishMessage) publish;
            assertThat(publishMessage.fixedHeader().isDup()).isTrue();
            assertThat(publishMessage.variableHeader().topicName()).isEqualTo("events/audit");
            assertThat(publishMessage.variableHeader().packetId()).isEqualTo(321);
            assertThat(publishMessage.payload().toString(UTF_8)).isEqualTo("created");
        } finally {
            ((MqttPublishMessage) publish).release();
        }

        MqttSubscribePayload subscribePayload = new MqttSubscribePayload(Arrays.asList(
                new MqttTopicSubscription("events/#", MqttQoS.AT_LEAST_ONCE)));
        MqttMessage subscribe = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(11),
                subscribePayload);
        assertThat(subscribe).isInstanceOf(MqttSubscribeMessage.class);
        assertThat(((MqttSubscribeMessage) subscribe).payload().topicSubscriptions().get(0).topicName())
                .isEqualTo("events/#");

        MqttUnsubscribePayload unsubscribePayload = new MqttUnsubscribePayload(Arrays.asList("events/#"));
        MqttMessage unsubscribe = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(12),
                unsubscribePayload);
        assertThat(unsubscribe).isInstanceOf(MqttUnsubscribeMessage.class);
        assertThat(((MqttUnsubscribeMessage) unsubscribe).payload().topics()).containsExactly("events/#");
    }

    @Test
    void pingDisconnectAndAuthMessagesUseFixedHeadersAndReasonCodeVariableHeaders() {
        MqttMessage pingReq = encodeAndDecode(MqttMessage.PINGREQ);
        MqttMessage pingResp = encodeAndDecode(MqttMessage.PINGRESP);
        MqttMessage disconnect = MqttMessageBuilders.disconnect()
                .reasonCode((byte) 0)
                .properties(MqttProperties.NO_PROPERTIES)
                .build();
        MqttMessage auth = MqttMessageBuilders.auth()
                .reasonCode((byte) 24)
                .properties(MqttProperties.NO_PROPERTIES)
                .build();

        assertThat(pingReq.fixedHeader().messageType()).isEqualTo(MqttMessageType.PINGREQ);
        assertThat(pingResp.fixedHeader().messageType()).isEqualTo(MqttMessageType.PINGRESP);
        assertThat(disconnect.fixedHeader().messageType()).isEqualTo(MqttMessageType.DISCONNECT);
        assertThat(((MqttReasonCodeAndPropertiesVariableHeader) disconnect.variableHeader()).reasonCode()).isZero();
        assertThat(auth.fixedHeader().messageType()).isEqualTo(MqttMessageType.AUTH);
        assertThat(((MqttReasonCodeAndPropertiesVariableHeader) auth.variableHeader()).reasonCode())
                .isEqualTo((byte) 24);
    }

    @Test
    void enumLookupsValidateProtocolLevelsMessageTypesAndReasonCodes() {
        assertThat(MqttVersion.fromProtocolNameAndLevel("MQTT", (byte) 4)).isEqualTo(MqttVersion.MQTT_3_1_1);
        assertThat(MqttVersion.fromProtocolNameAndLevel("MQTT", (byte) 5)).isEqualTo(MqttVersion.MQTT_5);
        assertThat(MqttVersion.MQTT_3_1.protocolName()).isEqualTo("MQIsdp");
        assertThat(MqttQoS.valueOf(0)).isEqualTo(MqttQoS.AT_MOST_ONCE);
        assertThat(MqttQoS.valueOf(1)).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(MqttQoS.valueOf(2)).isEqualTo(MqttQoS.EXACTLY_ONCE);
        assertThat(MqttQoS.valueOf(128)).isEqualTo(MqttQoS.FAILURE);
        assertThat(MqttMessageType.valueOf(3)).isEqualTo(MqttMessageType.PUBLISH);
        assertThat(MqttConnectReturnCode.valueOf((byte) 0)).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED);
        assertThat(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5.byteValue()).isEqualTo((byte) 0x87);
        assertThat(MqttProperties.MqttPropertyType.valueOf(
                MqttProperties.MqttPropertyType.USER_PROPERTY.value()))
                .isEqualTo(MqttProperties.MqttPropertyType.USER_PROPERTY);
        assertThatThrownBy(() -> MqttQoS.valueOf(3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MqttMessageType.valueOf(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MqttVersion.fromProtocolNameAndLevel("MQTT", (byte) 9))
                .isInstanceOf(MqttUnacceptableProtocolVersionException.class);
    }

    @Test
    void invalidFactoryMessagesExposeDecoderFailure() {
        IllegalArgumentException cause = new IllegalArgumentException("malformed packet");
        MqttMessage invalidWithoutHeader = MqttMessageFactory.newInvalidMessage(cause);
        MqttFixedHeader fixedHeader = new MqttFixedHeader(
                MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessage invalidWithHeader = MqttMessageFactory.newInvalidMessage(fixedHeader, null, cause);

        assertThat(invalidWithoutHeader.decoderResult().isFailure()).isTrue();
        assertThat(invalidWithoutHeader.decoderResult().cause()).isSameAs(cause);
        assertThat(invalidWithHeader.fixedHeader()).isSameAs(fixedHeader);
        assertThat(invalidWithHeader.decoderResult().isFailure()).isTrue();
        assertThat(invalidWithHeader.decoderResult().cause()).isSameAs(cause);
    }

    @Test
    void payloadValueObjectsExposeUnmodifiableViewsAndReasonCodeLists() {
        List<MqttTopicSubscription> topicSubscriptions = new ArrayList<>();
        topicSubscriptions.add(new MqttTopicSubscription(
                "commands/device-1",
                MqttSubscriptionOption.onlyFromQos(MqttQoS.AT_LEAST_ONCE)));
        MqttSubscribePayload subscribePayload = new MqttSubscribePayload(topicSubscriptions);

        List<String> topics = new ArrayList<>();
        topics.add("commands/device-1");
        MqttUnsubscribePayload unsubscribePayload = new MqttUnsubscribePayload(topics);

        MqttSubAckPayload subAckPayload = new MqttSubAckPayload(Arrays.asList(0, 1, 2, 128));
        MqttUnsubAckPayload unsubAckPayload = new MqttUnsubAckPayload(Arrays.asList((short) 0, (short) 17));

        assertThat(subscribePayload.topicSubscriptions()).hasSize(1);
        assertThat(subscribePayload.topicSubscriptions().get(0).topicName()).isEqualTo("commands/device-1");
        assertThat(subscribePayload.topicSubscriptions().get(0).qualityOfService()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(unsubscribePayload.topics()).containsExactly("commands/device-1");
        assertThatThrownBy(() -> subscribePayload.topicSubscriptions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> unsubscribePayload.topics().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(subAckPayload.grantedQoSLevels()).containsExactly(0, 1, 2, 128);
        assertThat(unsubAckPayload.unsubscribeReasonCodes()).containsExactly((short) 0, (short) 17);
        assertThat(MqttUnsubAckPayload.withEmptyDefaults(null).unsubscribeReasonCodes()).isEmpty();
    }

    private static MqttMessage encodeAndDecode(MqttMessage message) {
        ByteBuf encoded = encode(message);
        try {
            return decode(encoded.retainedDuplicate());
        } finally {
            encoded.release();
        }
    }

    private static MqttMessage encodeAndDecodeAfterMqtt5Connect(MqttMessage message) {
        ByteBuf encodedConnect = null;
        ByteBuf encodedMessage = null;
        EmbeddedChannel encoder = new EmbeddedChannel(MqttEncoder.INSTANCE);
        try {
            MqttConnectMessage connect = MqttMessageBuilders.connect()
                    .protocolVersion(MqttVersion.MQTT_5)
                    .clientId("mqtt5-session")
                    .cleanSession(true)
                    .keepAlive(30)
                    .properties(MqttProperties.NO_PROPERTIES)
                    .build();
            assertThat(encoder.writeOutbound(connect)).isTrue();
            encodedConnect = readAllOutboundBytes(encoder);
            assertThat(encoder.writeOutbound(message)).isTrue();
            encodedMessage = readAllOutboundBytes(encoder);
            return decodeAfterConnect(encodedConnect.retainedDuplicate(), encodedMessage.retainedDuplicate());
        } finally {
            if (encodedConnect != null) {
                encodedConnect.release();
            }
            if (encodedMessage != null) {
                encodedMessage.release();
            }
            encoder.finishAndReleaseAll();
        }
    }

    private static MqttMessage decodeAfterConnect(ByteBuf encodedConnect, ByteBuf encodedMessage) {
        EmbeddedChannel channel = new EmbeddedChannel(new MqttDecoder());
        try {
            assertThat(channel.writeInbound(encodedConnect)).isTrue();
            MqttConnectMessage connect = channel.readInbound();
            assertThat(connect.variableHeader().version()).isEqualTo(5);
            assertThat(channel.writeInbound(encodedMessage)).isTrue();
            MqttMessage message = channel.readInbound();
            assertThat(message).isNotNull();
            assertThat((Object) channel.readInbound()).isNull();
            return message;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static ByteBuf encode(MqttMessage message) {
        EmbeddedChannel channel = new EmbeddedChannel(MqttEncoder.INSTANCE);
        try {
            assertThat(channel.writeOutbound(message)).isTrue();
            ByteBuf encoded = readAllOutboundBytes(channel);
            assertThat(encoded.readableBytes()).isGreaterThan(0);
            return encoded;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static MqttMessage decode(ByteBuf encoded) {
        EmbeddedChannel channel = new EmbeddedChannel(new MqttDecoder());
        try {
            assertThat(channel.writeInbound(encoded)).isTrue();
            MqttMessage message = channel.readInbound();
            assertThat(message).isNotNull();
            assertThat((Object) channel.readInbound()).isNull();
            return message;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static ByteBuf readAllOutboundBytes(EmbeddedChannel channel) {
        ByteBuf aggregate = Unpooled.buffer();
        ByteBuf next = channel.readOutbound();
        while (next != null) {
            try {
                aggregate.writeBytes(next);
            } finally {
                next.release();
            }
            next = channel.readOutbound();
        }
        return aggregate;
    }
}
