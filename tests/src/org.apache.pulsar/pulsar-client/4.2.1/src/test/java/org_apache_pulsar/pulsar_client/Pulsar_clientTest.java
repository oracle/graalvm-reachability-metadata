/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.api.AuthenticationDataProvider;
import org.apache.pulsar.client.api.AuthenticationFactory;
import org.apache.pulsar.client.api.BatcherBuilder;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.CompressionType;
import org.apache.pulsar.client.api.ConsumerCryptoFailureAction;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.HashingScheme;
import org.apache.pulsar.client.api.KeySharedPolicy;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.MessagePayload;
import org.apache.pulsar.client.api.MessagePayloadFactory;
import org.apache.pulsar.client.api.MessageRoutingMode;
import org.apache.pulsar.client.api.ProducerAccessMode;
import org.apache.pulsar.client.api.ProducerCryptoFailureAction;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.PulsarClientSharedResources;
import org.apache.pulsar.client.api.Range;
import org.apache.pulsar.client.api.RegexSubscriptionMode;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SchemaSerializationException;
import org.apache.pulsar.client.api.ServiceUrlProvider;
import org.apache.pulsar.client.api.SizeUnit;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionMode;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.client.api.schema.GenericSchema;
import org.apache.pulsar.client.api.schema.RecordSchemaBuilder;
import org.apache.pulsar.client.api.schema.SchemaBuilder;
import org.apache.pulsar.client.api.schema.SchemaDefinition;
import org.apache.pulsar.common.schema.KeyValue;
import org.apache.pulsar.common.schema.KeyValueEncodingType;
import org.apache.pulsar.common.schema.SchemaInfo;
import org.apache.pulsar.common.schema.SchemaType;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class Pulsar_clientTest {
    private static final String UNAVAILABLE_SERVICE_URL = "pulsar://127.0.0.1:1";
    private static final String TOKEN_AUTH_PLUGIN_CLASS = "org.apache.pulsar.client.impl.auth.AuthenticationToken";

    @Test
    void primitiveTemporalAndPayloadSchemasRoundTripValues() throws Exception {
        assertThat(Schema.STRING.decode(Schema.STRING.encode("pulsar"))).isEqualTo("pulsar");
        assertThat(Schema.INT8.decode(Schema.INT8.encode((byte) 7))).isEqualTo((byte) 7);
        assertThat(Schema.INT16.decode(Schema.INT16.encode((short) 1024))).isEqualTo((short) 1024);
        assertThat(Schema.INT32.decode(Schema.INT32.encode(65_536))).isEqualTo(65_536);
        assertThat(Schema.INT64.decode(Schema.INT64.encode(9_876_543_210L))).isEqualTo(9_876_543_210L);
        assertThat(Schema.BOOL.decode(Schema.BOOL.encode(true))).isTrue();
        assertThat(Schema.FLOAT.decode(Schema.FLOAT.encode(12.5F))).isEqualTo(12.5F);
        assertThat(Schema.DOUBLE.decode(Schema.DOUBLE.encode(99.75D))).isEqualTo(99.75D);

        final LocalDate date = LocalDate.of(2026, 5, 8);
        final LocalTime time = LocalTime.of(10, 15, 30);
        final LocalDateTime dateTime = LocalDateTime.of(date, time);
        final Instant instant = Instant.parse("2026-05-08T10:15:30Z");
        assertThat(Schema.LOCAL_DATE.decode(Schema.LOCAL_DATE.encode(date))).isEqualTo(date);
        assertThat(Schema.LOCAL_TIME.decode(Schema.LOCAL_TIME.encode(time))).isEqualTo(time);
        assertThat(Schema.LOCAL_DATE_TIME.decode(Schema.LOCAL_DATE_TIME.encode(dateTime))).isEqualTo(dateTime);
        assertThat(Schema.INSTANT.decode(Schema.INSTANT.encode(instant))).isEqualTo(instant);

        final byte[] encodedBuffer = Schema.BYTEBUFFER.encode(ByteBuffer.wrap(new byte[] {1, 2, 3, 4}));
        final ByteBuffer decodedBuffer = Schema.BYTEBUFFER.decode(encodedBuffer);
        final byte[] decodedBytes = new byte[decodedBuffer.remaining()];
        decodedBuffer.get(decodedBytes);
        assertThat(decodedBytes).containsExactly(1, 2, 3, 4);

        final MessagePayload byteArrayPayload = MessagePayloadFactory.DEFAULT.wrap(new byte[] {5, 6, 7});
        assertThat(byteArrayPayload.copiedBuffer()).containsExactly(5, 6, 7);
        byteArrayPayload.release();

        final MessagePayload byteBufferPayload = MessagePayloadFactory.DEFAULT.wrap(ByteBuffer.wrap(new byte[] {8, 9}));
        assertThat(byteBufferPayload.copiedBuffer()).containsExactly(8, 9);
        byteBufferPayload.release();
    }

    @Test
    void structKeyValueAndGenericSchemasRoundTripRecords() {
        final SensorReading reading = new SensorReading();
        reading.setSensorId("sensor-a");
        reading.setSequence(42L);
        reading.setTemperature(21.75D);
        reading.setActive(true);

        final Schema<SensorReading> jsonSchema = Schema.JSON(SensorReading.class);
        final SensorReading jsonDecoded = jsonSchema.decode(jsonSchema.encode(reading));
        assertThat(jsonDecoded.getSensorId()).isEqualTo("sensor-a");
        assertThat(jsonDecoded.getSequence()).isEqualTo(42L);
        assertThat(jsonDecoded.getTemperature()).isEqualTo(21.75D);
        assertThat(jsonDecoded.isActive()).isTrue();

        final SchemaDefinition<SensorReading> avroDefinition = SchemaDefinition.<SensorReading>builder()
                .withPojo(SensorReading.class)
                .withAlwaysAllowNull(false)
                .addProperty("source", "integration-test")
                .build();
        final Schema<SensorReading> avroSchema = Schema.AVRO(avroDefinition);
        final SensorReading avroDecoded = avroSchema.decode(avroSchema.encode(reading));
        assertThat(avroDecoded.getSensorId()).isEqualTo(reading.getSensorId());
        assertThat(avroDecoded.getSequence()).isEqualTo(reading.getSequence());
        assertThat(avroDecoded.getTemperature()).isEqualTo(reading.getTemperature());
        assertThat(avroDecoded.isActive()).isEqualTo(reading.isActive());
        assertThat(avroSchema.getSchemaInfo().getProperties()).containsEntry("source", "integration-test");

        final Schema<KeyValue<String, Integer>> keyValueSchema = Schema.KeyValue(
                Schema.STRING, Schema.INT32, KeyValueEncodingType.INLINE);
        final KeyValue<String, Integer> keyValue = new KeyValue<>("partition-key", 17);
        final KeyValue<String, Integer> keyValueDecoded = keyValueSchema.decode(keyValueSchema.encode(keyValue));
        assertThat(keyValueDecoded.getKey()).isEqualTo("partition-key");
        assertThat(keyValueDecoded.getValue()).isEqualTo(17);

        final RecordSchemaBuilder recordBuilder = SchemaBuilder.record("Coordinates");
        recordBuilder.property("purpose", "generic-record-test");
        recordBuilder.field("x").type(SchemaType.INT32).required();
        recordBuilder.field("label").type(SchemaType.STRING).optional().defaultValue("unknown");
        final SchemaInfo schemaInfo = recordBuilder.build(SchemaType.JSON);
        final GenericSchema<GenericRecord> genericSchema = Schema.generic(schemaInfo);
        final GenericRecord genericRecord = genericSchema.newRecordBuilder()
                .set("x", 12)
                .set("label", "north")
                .build();
        final GenericRecord genericDecoded = genericSchema.decode(genericSchema.encode(genericRecord));
        assertThat(genericSchema.getFields()).extracting(field -> field.getName()).containsExactly("x", "label");
        assertThat(genericDecoded.getField("x")).isEqualTo(12);
        assertThat(genericDecoded.getField("label")).isEqualTo("north");
    }

    @Test
    void autoProduceBytesSchemaValidatesPayloadsAgainstWrappedSchema() {
        final Schema<byte[]> autoProduceSchema = Schema.AUTO_PRODUCE_BYTES(Schema.INT32);
        final byte[] validPayload = Schema.INT32.encode(123_456);

        assertThat(autoProduceSchema.getSchemaInfo().getType()).isEqualTo(SchemaType.INT32);
        assertThat(autoProduceSchema.encode(validPayload)).containsExactly(validPayload);
        assertThat(Schema.INT32.decode(autoProduceSchema.decode(validPayload, new byte[] {1, 2}))).isEqualTo(123_456);

        final Schema<byte[]> clonedSchema = autoProduceSchema.clone();
        assertThat(clonedSchema.getSchemaInfo().getType()).isEqualTo(SchemaType.INT32);
        assertThat(clonedSchema.encode(validPayload)).containsExactly(validPayload);

        assertThrows(SchemaSerializationException.class, () -> autoProduceSchema.encode(new byte[] {1, 2, 3}));
    }

    @Test
    void authenticationFactoriesProduceUsableAuthenticationData() throws Exception {
        try (Authentication tokenAuthentication = AuthenticationFactory.token(() -> "supplied-token")) {
            tokenAuthentication.start();

            final AuthenticationDataProvider data = tokenAuthentication.getAuthData("pulsar://broker.example:6650");

            assertThat(tokenAuthentication.getAuthMethodName()).isEqualTo("token");
            assertThat(data.hasDataFromCommand()).isTrue();
            assertThat(data.getCommandData()).isEqualTo("supplied-token");
            assertThat(data.hasDataForHttp()).isTrue();
            assertThat(data.getHttpHeaders()).anySatisfy(header -> {
                assertThat(header.getKey()).isEqualTo("Authorization");
                assertThat(header.getValue()).isEqualTo("Bearer supplied-token");
            });
        }

        try {
            try (Authentication pluginAuthentication = AuthenticationFactory.create(
                    TOKEN_AUTH_PLUGIN_CLASS, "token:plugin-token")) {
                pluginAuthentication.start();

                final AuthenticationDataProvider pluginData = pluginAuthentication.getAuthData(
                        "pulsar://broker.example:6650");

                assertThat(pluginAuthentication.getAuthMethodName()).isEqualTo("token");
                assertThat(pluginData.getCommandData()).isEqualTo("plugin-token");
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void policiesRangesAndMessageIdsUsePublicValueObjects() throws IOException {
        final BatchReceivePolicy batchReceivePolicy = BatchReceivePolicy.builder()
                .maxNumMessages(10)
                .maxNumBytes(4096)
                .timeout(500, TimeUnit.MILLISECONDS)
                .messagesFromMultiTopicsEnabled(true)
                .build();
        batchReceivePolicy.verify();
        assertThat(batchReceivePolicy.getMaxNumMessages()).isEqualTo(10);
        assertThat(batchReceivePolicy.getMaxNumBytes()).isEqualTo(4096);
        assertThat(batchReceivePolicy.getTimeoutMs()).isEqualTo(500L);
        assertThat(batchReceivePolicy.isMessagesFromMultiTopicsEnabled()).isTrue();

        final DeadLetterPolicy deadLetterPolicy = DeadLetterPolicy.builder()
                .maxRedeliverCount(3)
                .retryLetterTopic("persistent://public/default/retry-topic")
                .deadLetterTopic("persistent://public/default/dead-letter-topic")
                .initialSubscriptionName("retry-subscription")
                .build();
        assertThat(deadLetterPolicy.getMaxRedeliverCount()).isEqualTo(3);
        assertThat(deadLetterPolicy.getRetryLetterTopic()).endsWith("retry-topic");
        assertThat(deadLetterPolicy.getDeadLetterTopic()).endsWith("dead-letter-topic");
        assertThat(deadLetterPolicy.getInitialSubscriptionName()).isEqualTo("retry-subscription");

        final Range firstRange = Range.of(0, 99);
        final Range secondRange = Range.of(50, 150);
        final Range intersection = firstRange.intersect(secondRange);
        assertThat(firstRange.contains(25)).isTrue();
        assertThat(firstRange.contains(Range.of(10, 20))).isTrue();
        assertThat(intersection.getStart()).isEqualTo(50);
        assertThat(intersection.getEnd()).isEqualTo(99);
        assertThat(intersection.size()).isEqualTo(50);

        final KeySharedPolicy stickyPolicy = KeySharedPolicy.stickyHashRange().ranges(firstRange);
        stickyPolicy.validate();
        assertThat(stickyPolicy.isAllowOutOfOrderDelivery()).isFalse();
        assertThat(stickyPolicy.getHashRangeTotal()).isEqualTo(65_536);
        final KeySharedPolicy autoSplitPolicy = KeySharedPolicy.autoSplitHashRange().setAllowOutOfOrderDelivery(true);
        autoSplitPolicy.validate();
        assertThat(autoSplitPolicy.isAllowOutOfOrderDelivery()).isTrue();

        final byte[] earliestBytes = MessageId.earliest.toByteArray();
        final MessageId earliestFromBytes = MessageId.fromByteArray(earliestBytes);
        final MessageId earliestWithTopic = MessageId.fromByteArrayWithTopic(
                earliestBytes, "persistent://public/default/message-id-topic");
        assertThat(earliestFromBytes).isEqualTo(MessageId.earliest);
        assertThat(earliestWithTopic.toByteArray()).isEqualTo(earliestBytes);
        assertThat(MessageId.earliest.compareTo(MessageId.latest)).isLessThan(0);
    }

    @Test
    void configuredClientBuildersFailFastAgainstUnavailableBroker() throws Exception {
        final StaticServiceUrlProvider serviceUrlProvider = new StaticServiceUrlProvider(UNAVAILABLE_SERVICE_URL);
        try (PulsarClientSharedResources sharedResources = PulsarClientSharedResources.builder()
                .resourceTypes(
                        PulsarClientSharedResources.SharedResource.EventLoopGroup,
                        PulsarClientSharedResources.SharedResource.ListenerExecutor,
                        PulsarClientSharedResources.SharedResource.Timer,
                        PulsarClientSharedResources.SharedResource.InternalExecutor,
                        PulsarClientSharedResources.SharedResource.ScheduledExecutor,
                        PulsarClientSharedResources.SharedResource.LookupExecutor)
                .configureEventLoop(config -> config
                        .name("pulsar-test-event-loop")
                        .numberOfThreads(1)
                        .daemon(true)
                        .enableBusyWait(false))
                .configureThreadPool(PulsarClientSharedResources.SharedResource.ListenerExecutor, config -> config
                        .name("pulsar-test-listener")
                        .numberOfThreads(1)
                        .daemon(true))
                .configureThreadPool(PulsarClientSharedResources.SharedResource.InternalExecutor, config -> config
                        .name("pulsar-test-internal")
                        .numberOfThreads(1)
                        .daemon(true))
                .configureThreadPool(PulsarClientSharedResources.SharedResource.ScheduledExecutor, config -> config
                        .name("pulsar-test-scheduled")
                        .numberOfThreads(1)
                        .daemon(true))
                .configureThreadPool(PulsarClientSharedResources.SharedResource.LookupExecutor, config -> config
                        .name("pulsar-test-lookup")
                        .numberOfThreads(1)
                        .daemon(true))
                .configureTimer(config -> config.name("pulsar-test-timer").tickDuration(10, TimeUnit.MILLISECONDS))
                .build();
                Authentication authentication = AuthenticationFactory.token("client-token");
                PulsarClient client = PulsarClient.builder()
                        .serviceUrlProvider(serviceUrlProvider)
                        .sharedResources(sharedResources)
                        .authentication(authentication)
                        .operationTimeout(1, TimeUnit.SECONDS)
                        .lookupTimeout(1, TimeUnit.SECONDS)
                        .connectionTimeout(1, TimeUnit.SECONDS)
                        .startingBackoffInterval(1, TimeUnit.MILLISECONDS)
                        .maxBackoffInterval(10, TimeUnit.MILLISECONDS)
                        .connectionMaxIdleSeconds(15)
                        .keepAliveInterval(1, TimeUnit.SECONDS)
                        .ioThreads(1)
                        .listenerThreads(1)
                        .connectionsPerBroker(1)
                        .memoryLimit(4, SizeUnit.MEGA_BYTES)
                        .enableTcpNoDelay(true)
                        .allowTlsInsecureConnection(true)
                        .enableTlsHostnameVerification(false)
                        .description("native-image integration test client")
                        .build()) {
            assertThat(serviceUrlProvider.getInitializeCalls()).isEqualTo(1);
            assertThat(client.isClosed()).isFalse();

            assertCompletesWithPulsarClientException(client.newProducer(Schema.STRING)
                    .topic("persistent://public/default/integration-producer")
                    .producerName("producer-under-test")
                    .accessMode(ProducerAccessMode.Shared)
                    .sendTimeout(1, TimeUnit.SECONDS)
                    .maxPendingMessages(4)
                    .blockIfQueueFull(false)
                    .messageRoutingMode(MessageRoutingMode.RoundRobinPartition)
                    .hashingScheme(HashingScheme.JavaStringHash)
                    .compressionType(CompressionType.LZ4)
                    .enableBatching(true)
                    .batcherBuilder(BatcherBuilder.KEY_BASED)
                    .batchingMaxPublishDelay(1, TimeUnit.MILLISECONDS)
                    .batchingMaxMessages(2)
                    .cryptoFailureAction(ProducerCryptoFailureAction.SEND)
                    .property("producer-property", "value")
                    .createAsync());

            assertCompletesWithPulsarClientException(client.newConsumer(Schema.STRING)
                    .topic("persistent://public/default/integration-consumer")
                    .subscriptionName("subscription-under-test")
                    .subscriptionType(SubscriptionType.Key_Shared)
                    .subscriptionMode(SubscriptionMode.Durable)
                    .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                    .subscriptionTopicsMode(RegexSubscriptionMode.PersistentOnly)
                    .receiverQueueSize(1)
                    .ackTimeout(1, TimeUnit.SECONDS)
                    .negativeAckRedeliveryDelay(1, TimeUnit.MILLISECONDS)
                    .batchReceivePolicy(BatchReceivePolicy.builder()
                            .maxNumMessages(2)
                            .maxNumBytes(2048)
                            .timeout(100, TimeUnit.MILLISECONDS)
                            .build())
                    .deadLetterPolicy(DeadLetterPolicy.builder()
                            .maxRedeliverCount(2)
                            .deadLetterTopic("persistent://public/default/integration-dead-letter")
                            .build())
                    .keySharedPolicy(KeySharedPolicy.stickyHashRange().ranges(Range.of(0, 100)))
                    .negativeAckRedeliveryBackoff(redeliveryCount -> Math.min(100L, redeliveryCount * 5L))
                    .ackTimeoutRedeliveryBackoff(redeliveryCount -> Math.min(100L, redeliveryCount * 10L))
                    .cryptoFailureAction(ConsumerCryptoFailureAction.CONSUME)
                    .property("consumer-property", "value")
                    .subscribeAsync());

            assertCompletesWithPulsarClientException(client.newReader(Schema.STRING)
                    .topic("persistent://public/default/integration-reader")
                    .startMessageId(MessageId.earliest)
                    .startMessageIdInclusive()
                    .readerName("reader-under-test")
                    .receiverQueueSize(1)
                    .readCompacted(false)
                    .keyHashRange(Range.of(0, 100))
                    .autoUpdatePartitions(false)
                    .cryptoFailureAction(ConsumerCryptoFailureAction.CONSUME)
                    .createAsync());

            assertCompletesWithPulsarClientException(client.newTableViewBuilder(Schema.STRING)
                    .topic("persistent://public/default/integration-table-view")
                    .subscriptionName("table-view-subscription")
                    .autoUpdatePartitionsInterval(1, TimeUnit.SECONDS)
                    .cryptoFailureAction(ConsumerCryptoFailureAction.CONSUME)
                    .createAsync());
        }
        assertThat(serviceUrlProvider.getCloseCalls()).isEqualTo(1);
    }

    private static void assertCompletesWithPulsarClientException(CompletableFuture<?> future) {
        final ExecutionException exception = assertThrows(
                ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
        assertThat(exception.getCause()).isInstanceOf(PulsarClientException.class);
    }

    public static class SensorReading {
        private String sensorId;
        private long sequence;
        private double temperature;
        private boolean active;

        public SensorReading() {
        }

        public String getSensorId() {
            return sensorId;
        }

        public void setSensorId(String sensorId) {
            this.sensorId = sensorId;
        }

        public long getSequence() {
            return sequence;
        }

        public void setSequence(long sequence) {
            this.sequence = sequence;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    private static final class StaticServiceUrlProvider implements ServiceUrlProvider {
        private final String serviceUrl;
        private final AtomicInteger initializeCalls = new AtomicInteger();
        private final AtomicInteger closeCalls = new AtomicInteger();

        private StaticServiceUrlProvider(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        @Override
        public void initialize(PulsarClient client) {
            assertThat(client).isNotNull();
            initializeCalls.incrementAndGet();
        }

        @Override
        public String getServiceUrl() {
            return serviceUrl;
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
        }

        private int getInitializeCalls() {
            return initializeCalls.get();
        }

        private int getCloseCalls() {
            return closeCalls.get();
        }
    }
}
