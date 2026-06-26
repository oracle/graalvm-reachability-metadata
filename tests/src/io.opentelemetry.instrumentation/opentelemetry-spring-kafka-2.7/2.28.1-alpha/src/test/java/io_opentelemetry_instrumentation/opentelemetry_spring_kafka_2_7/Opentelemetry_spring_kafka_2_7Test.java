/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_spring_kafka_2_7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;

public class Opentelemetry_spring_kafka_2_7Test {
    private static final String TOPIC = "orders";

    @Test
    void recordInterceptorCreatedFromFactoryProcessesSuccessfulAndFailedRecords() {
        SpringKafkaTelemetry telemetry = SpringKafkaTelemetry.create(OpenTelemetry.noop());
        RecordInterceptor<String, String> interceptor = telemetry.createRecordInterceptor();

        ConsumerRecord<String, String> successfulRecord = record(0, 1L, "first", "created");
        ConsumerRecord<String, String> interceptedSuccessfulRecord = interceptor.intercept(successfulRecord, null);
        assertThat(interceptedSuccessfulRecord).isSameAs(successfulRecord);
        interceptor.success(successfulRecord, null);

        ConsumerRecord<String, String> failedRecord = record(0, 2L, "second", "failed");
        ConsumerRecord<String, String> interceptedFailedRecord = interceptor.intercept(failedRecord, null);
        assertThat(interceptedFailedRecord).isSameAs(failedRecord);
        interceptor.failure(failedRecord, new IllegalStateException("listener failed"), null);
    }

    @Test
    void recordInterceptorWrapsDecoratedInterceptorAndPropagatesThreadLifecycleCallbacks() {
        SpringKafkaTelemetry telemetry = SpringKafkaTelemetry.builder(OpenTelemetry.noop())
                .setCapturedHeaders(List.of("trace-test-header"))
                .setCaptureExperimentalSpanAttributes(true)
                .build();
        ConsumerRecord<String, String> successfulRecord = record(1, 10L, "input", "value");
        ConsumerRecord<String, String> failedRecord = record(1, 11L, "failed", "value");
        ConsumerRecord<String, String> replacementRecord = record(1, 12L, "replacement", "decorated");
        List<String> callbacks = new ArrayList<>();
        RecordingRecordInterceptor decorated = new RecordingRecordInterceptor(replacementRecord, callbacks);
        RecordInterceptor<String, String> interceptor = telemetry.createRecordInterceptor(decorated);

        interceptor.setupThreadState(null);
        ConsumerRecord<String, String> result = interceptor.intercept(successfulRecord, null);
        interceptor.success(successfulRecord, null);
        interceptor.afterRecord(successfulRecord, null);
        interceptor.clearThreadState(null);

        interceptor.setupThreadState(null);
        IllegalArgumentException listenerError = new IllegalArgumentException("boom");
        interceptor.intercept(failedRecord, null);
        interceptor.failure(failedRecord, listenerError, null);
        interceptor.afterRecord(failedRecord, null);
        interceptor.clearThreadState(null);

        assertThat(result).isSameAs(replacementRecord);
        assertThat(decorated.failure).isSameAs(listenerError);
        assertThat(callbacks)
                .containsExactly("setup", "intercept", "success", "afterRecord", "clear",
                        "setup", "intercept", "failure", "afterRecord", "clear");
    }

    @Test
    void batchInterceptorCreatedFromFactoryProcessesSuccessfulAndFailedBatches() {
        SpringKafkaTelemetry telemetry = SpringKafkaTelemetry.create(OpenTelemetry.noop());
        BatchInterceptor<String, String> interceptor = telemetry.createBatchInterceptor();

        ConsumerRecords<String, String> successfulBatch = records(record(0, 20L, "one", "ok"),
                record(1, 21L, "two", "ok"));
        ConsumerRecords<String, String> interceptedSuccessfulBatch = interceptor.intercept(successfulBatch, null);
        assertThat(interceptedSuccessfulBatch).isSameAs(successfulBatch);
        interceptor.success(successfulBatch, null);

        ConsumerRecords<String, String> failedBatch = records(record(0, 22L, "three", "bad"));
        ConsumerRecords<String, String> interceptedFailedBatch = interceptor.intercept(failedBatch, null);
        assertThat(interceptedFailedBatch).isSameAs(failedBatch);
        interceptor.failure(failedBatch, new IllegalStateException("batch listener failed"), null);
    }

    @Test
    void batchInterceptorWrapsDecoratedInterceptorAndPropagatesCallbacks() {
        SpringKafkaTelemetry telemetry = SpringKafkaTelemetry.builder(OpenTelemetry.noop())
                .setMessagingReceiveTelemetryEnabled(true)
                .build();
        ConsumerRecords<String, String> successfulBatch = records(record(0, 30L, "input", "value"));
        ConsumerRecords<String, String> failedBatch = records(record(0, 31L, "failed", "value"));
        ConsumerRecords<String, String> replacementBatch = records(record(0, 32L, "replacement", "decorated"));
        List<String> callbacks = new ArrayList<>();
        RecordingBatchInterceptor decorated = new RecordingBatchInterceptor(replacementBatch, callbacks);
        BatchInterceptor<String, String> interceptor = telemetry.createBatchInterceptor(decorated);

        interceptor.setupThreadState(null);
        ConsumerRecords<String, String> result = interceptor.intercept(successfulBatch, null);
        interceptor.success(successfulBatch, null);
        interceptor.clearThreadState(null);

        interceptor.setupThreadState(null);
        IllegalArgumentException listenerError = new IllegalArgumentException("boom");
        interceptor.intercept(failedBatch, null);
        interceptor.failure(failedBatch, listenerError, null);
        interceptor.clearThreadState(null);

        assertThat(result).isSameAs(replacementBatch);
        assertThat(decorated.failure).isSameAs(listenerError);
        assertThat(callbacks)
                .containsExactly("setup", "intercept", "success", "clear",
                        "setup", "intercept", "failure", "clear");
    }

    @Test
    void recordInterceptorPropagatesDecoratedSuccessCallbackFailure() {
        SpringKafkaTelemetry telemetry = SpringKafkaTelemetry.create(OpenTelemetry.noop());
        IllegalStateException callbackFailure = new IllegalStateException("success callback failed");
        ThrowingSuccessRecordInterceptor decorated = new ThrowingSuccessRecordInterceptor(callbackFailure);
        RecordInterceptor<String, String> interceptor = telemetry.createRecordInterceptor(decorated);
        ConsumerRecord<String, String> record = record(2, 40L, "failed", "value");

        ConsumerRecord<String, String> result = interceptor.intercept(record, null);
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> interceptor.success(record, null));

        assertThat(result).isSameAs(record);
        assertThat(thrown).isSameAs(callbackFailure);
        assertThat(decorated.callbacks).containsExactly("intercept", "success");
    }

    private static ConsumerRecord<String, String> record(int partition, long offset, String key, String value) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, partition, offset, key, value);
        record.headers().add("trace-test-header", (key + ":" + value).getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @SafeVarargs
    private static ConsumerRecords<String, String> records(ConsumerRecord<String, String>... records) {
        Map<TopicPartition, List<ConsumerRecord<String, String>>> recordsByPartition = new LinkedHashMap<>();
        for (ConsumerRecord<String, String> record : records) {
            TopicPartition partition = new TopicPartition(record.topic(), record.partition());
            List<ConsumerRecord<String, String>> partitionRecords = recordsByPartition.get(partition);
            if (partitionRecords == null) {
                partitionRecords = new ArrayList<>();
                recordsByPartition.put(partition, partitionRecords);
            }
            partitionRecords.add(record);
        }
        return new ConsumerRecords<>(recordsByPartition);
    }

    private static final class RecordingRecordInterceptor implements RecordInterceptor<String, String> {
        private final ConsumerRecord<String, String> replacement;
        private final List<String> callbacks;
        private Exception failure;

        private RecordingRecordInterceptor(ConsumerRecord<String, String> replacement, List<String> callbacks) {
            this.replacement = replacement;
            this.callbacks = callbacks;
        }

        @Override
        @SuppressWarnings("deprecation")
        public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record) {
            return intercept(record, null);
        }

        @Override
        public ConsumerRecord<String, String> intercept(
                ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
            callbacks.add("intercept");
            assertThat(record).isNotSameAs(replacement);
            return replacement;
        }

        @Override
        public void success(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
            callbacks.add("success");
        }

        @Override
        public void failure(
                ConsumerRecord<String, String> record, Exception exception, Consumer<String, String> consumer) {
            callbacks.add("failure");
            failure = exception;
        }

        @Override
        public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
            callbacks.add("afterRecord");
        }

        @Override
        public void setupThreadState(Consumer<?, ?> consumer) {
            callbacks.add("setup");
        }

        @Override
        public void clearThreadState(Consumer<?, ?> consumer) {
            callbacks.add("clear");
        }
    }

    private static final class RecordingBatchInterceptor implements BatchInterceptor<String, String> {
        private final ConsumerRecords<String, String> replacement;
        private final List<String> callbacks;
        private Exception failure;

        private RecordingBatchInterceptor(ConsumerRecords<String, String> replacement, List<String> callbacks) {
            this.replacement = replacement;
            this.callbacks = callbacks;
        }

        @Override
        public ConsumerRecords<String, String> intercept(
                ConsumerRecords<String, String> records, Consumer<String, String> consumer) {
            callbacks.add("intercept");
            assertThat(records).isNotSameAs(replacement);
            return replacement;
        }

        @Override
        public void success(ConsumerRecords<String, String> records, Consumer<String, String> consumer) {
            callbacks.add("success");
        }

        @Override
        public void failure(
                ConsumerRecords<String, String> records, Exception exception, Consumer<String, String> consumer) {
            callbacks.add("failure");
            failure = exception;
        }

        @Override
        public void setupThreadState(Consumer<?, ?> consumer) {
            callbacks.add("setup");
        }

        @Override
        public void clearThreadState(Consumer<?, ?> consumer) {
            callbacks.add("clear");
        }
    }

    private static final class ThrowingSuccessRecordInterceptor implements RecordInterceptor<String, String> {
        private final IllegalStateException exception;
        private final List<String> callbacks = new ArrayList<>();

        private ThrowingSuccessRecordInterceptor(IllegalStateException exception) {
            this.exception = exception;
        }

        @Override
        @SuppressWarnings("deprecation")
        public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record) {
            return intercept(record, null);
        }

        @Override
        public ConsumerRecord<String, String> intercept(
                ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
            callbacks.add("intercept");
            return record;
        }

        @Override
        public void success(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
            callbacks.add("success");
            throw exception;
        }
    }
}
