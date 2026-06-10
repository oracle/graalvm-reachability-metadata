/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client;

import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.api.AuthenticationDataProvider;
import org.apache.pulsar.client.api.AuthenticationFactory;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.ReaderBuilder;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.schema.KeyValue;
import org.apache.pulsar.common.schema.KeyValueEncodingType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PulsarClientTest {
    @Test
    void createsClientAndOperationBuildersWithoutOpeningConnections() throws Exception {
        try (Authentication authentication = AuthenticationFactory.token("builder-token");
                PulsarClient client = PulsarClient.builder()
                        .serviceUrl("pulsar://localhost:6650")
                        .authentication(authentication)
                        .connectionTimeout(1, TimeUnit.SECONDS)
                        .operationTimeout(1, TimeUnit.SECONDS)
                        .build()) {
            ProducerBuilder<String> producerBuilder = client.newProducer(Schema.STRING)
                    .topic(uniqueTopic("producer"))
                    .producerName("producer-" + UUID.randomUUID())
                    .enableBatching(false)
                    .sendTimeout(1, TimeUnit.SECONDS);
            ConsumerBuilder<String> consumerBuilder = client.newConsumer(Schema.STRING)
                    .topic(uniqueTopic("consumer"))
                    .subscriptionName("subscription-" + UUID.randomUUID())
                    .subscriptionType(SubscriptionType.Shared)
                    .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                    .deadLetterPolicy(DeadLetterPolicy.builder()
                            .maxRedeliverCount(2)
                            .deadLetterTopic(uniqueTopic("dead-letter"))
                            .build())
                    .receiverQueueSize(16);
            ReaderBuilder<String> readerBuilder = client.newReader(Schema.STRING)
                    .topic(uniqueTopic("reader"))
                    .readerName("reader-" + UUID.randomUUID())
                    .startMessageId(MessageId.earliest);

            assertThat(producerBuilder).isNotNull();
            assertThat(consumerBuilder).isNotNull();
            assertThat(readerBuilder).isNotNull();
        }
    }

    @Test
    void encodesAndDecodesPrimitiveAndKeyValueSchemas() {
        String text = "pulsar-schema";
        byte[] textBytes = Schema.STRING.encode(text);
        assertThat(Schema.STRING.decode(textBytes)).isEqualTo(text);

        byte[] rawBytes = "payload".getBytes(StandardCharsets.UTF_8);
        assertThat(Schema.BYTES.decode(Schema.BYTES.encode(rawBytes))).isEqualTo(rawBytes);

        ByteBuffer buffer = ByteBuffer.wrap("buffer".getBytes(StandardCharsets.UTF_8));
        ByteBuffer decodedBuffer = Schema.BYTEBUFFER.decode(Schema.BYTEBUFFER.encode(buffer));
        assertThat(decodedBuffer.remaining()).isEqualTo("buffer".length());
        assertThat(StandardCharsets.UTF_8.decode(decodedBuffer).toString()).isEqualTo("buffer");

        assertThat(Schema.BOOL.decode(Schema.BOOL.encode(true))).isTrue();
        assertThat(Schema.INT32.decode(Schema.INT32.encode(42))).isEqualTo(42);
        assertThat(Schema.INT64.decode(Schema.INT64.encode(123L))).isEqualTo(123L);
        assertThat(Schema.FLOAT.decode(Schema.FLOAT.encode(1.5F))).isEqualTo(1.5F);
        assertThat(Schema.DOUBLE.decode(Schema.DOUBLE.encode(2.5D))).isEqualTo(2.5D);

        KeyValue<String, Integer> keyValue = new KeyValue<>("primary", 7);
        Schema<KeyValue<String, Integer>> keyValueSchema = Schema.KeyValue(
                Schema.STRING,
                Schema.INT32,
                KeyValueEncodingType.INLINE
        );
        KeyValue<String, Integer> decodedKeyValue = keyValueSchema.decode(keyValueSchema.encode(keyValue));
        assertThat(decodedKeyValue.getKey()).isEqualTo("primary");
        assertThat(decodedKeyValue.getValue()).isEqualTo(7);
    }

    @Test
    void createsAuthenticationDataAndMessageIdUtilities() throws Exception {
        try (Authentication authentication = AuthenticationFactory.token("sample-token")) {
            authentication.start();
            AuthenticationDataProvider authData = authentication.getAuthData("broker.example.com");

            assertThat(authentication.getAuthMethodName()).isEqualTo("token");
            assertThat(authData.hasDataForHttp()).isTrue();
            assertThat(authData.getHttpHeaders()).anySatisfy(header -> {
                assertThat(header.getKey()).isEqualTo("Authorization");
                assertThat(header.getValue()).isEqualTo("Bearer sample-token");
            });
        }

        assertThat(MessageId.fromByteArray(MessageId.earliest.toByteArray())).isEqualTo(MessageId.earliest);
        assertThat(MessageId.fromByteArray(MessageId.latest.toByteArray())).isEqualTo(MessageId.latest);
    }

    private static String uniqueTopic(String suffix) {
        return "persistent://public/default/test-" + suffix + "-" + UUID.randomUUID();
    }
}
