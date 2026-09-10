/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.test.MockRecordCollector;
import org.apache.kafka.streams.StreamsMetrics;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.Punctuator;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.StateRestoreCallback;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.api.RecordMetadata;
import org.apache.kafka.streams.processor.StateStoreContext;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Small concrete test doubles that work in JVM and Native Image test runs. */
public final class NativeCoverageFixtures {
    private NativeCoverageFixtures() {
    }

    static MockRecordCollector recordCollector() {
        return new MockRecordCollector();
    }

    public static ProcessorContext processorContext(long timestamp) {
        return new SimpleProcessorContext(timestamp);
    }

    static StateStoreContext stateStoreContext() {
        return new SimpleStateStoreContext();
    }

    static final class SimpleProcessorContext implements ProcessorContext {
        private final long timestamp;
        private boolean committed;

        SimpleProcessorContext(long timestamp) {
            this.timestamp = timestamp;
        }

        boolean committed() {
            return committed;
        }

        @Override
        public String applicationId() {
            return "coverage";
        }

        @Override
        public TaskId taskId() {
            return new TaskId(0, 0);
        }

        @Override
        public Serde<?> keySerde() {
            return Serdes.String();
        }

        @Override
        public Serde<?> valueSerde() {
            return Serdes.String();
        }

        @Override
        public File stateDir() {
            return new File("build/native-coverage-state");
        }

        @Override
        public StreamsMetrics metrics() {
            return null;
        }

        @Override
        public void register(StateStore store, StateRestoreCallback callback) {
        }

        @Override
        public <S extends StateStore> S getStateStore(String name) {
            return null;
        }

        @Override
        public Cancellable schedule(Duration interval, PunctuationType type, Punctuator punctuator) {
            return () -> { };
        }

        @Override
        public <K, V> void forward(K key, V value) {
            throw new RuntimeException("forward disabled for coverage fixture");
        }

        @Override
        public <K, V> void forward(K key, V value, To to) {
            throw new RuntimeException("forward disabled for coverage fixture");
        }

        @Override
        public void commit() {
            committed = true;
        }

        @Override
        public String topic() {
            return "orders";
        }

        @Override
        public int partition() {
            return 3;
        }

        @Override
        public long offset() {
            return 17L;
        }

        @Override
        public Headers headers() {
            return new RecordHeaders();
        }

        @Override
        public long timestamp() {
            return timestamp;
        }

        @Override
        public Map<String, Object> appConfigs() {
            return Map.of("prefix.key", "value");
        }

        @Override
        public Map<String, Object> appConfigsWithPrefix(String prefix) {
            return Map.of("key", "value");
        }

        @Override
        public long currentSystemTimeMs() {
            return 1000L;
        }

        @Override
        public long currentStreamTimeMs() {
            return 900L;
        }
    }

    private static final class SimpleStateStoreContext implements StateStoreContext {
        @Override
        public String applicationId() {
            return "coverage";
        }

        @Override
        public TaskId taskId() {
            return new TaskId(0, 0);
        }

        @Override
        public Optional<RecordMetadata> recordMetadata() {
            return Optional.empty();
        }

        @Override
        public Serde<?> keySerde() {
            return Serdes.String();
        }

        @Override
        public Serde<?> valueSerde() {
            return Serdes.String();
        }

        @Override
        public File stateDir() {
            return new File("build/native-coverage-state");
        }

        @Override
        public StreamsMetrics metrics() {
            return null;
        }

        @Override
        public void register(StateStore store, StateRestoreCallback callback) {
        }

        @Override
        public void register(StateStore store, StateRestoreCallback callback,
                org.apache.kafka.streams.processor.CommitCallback commitCallback) {
        }

        @Override
        public Map<String, Object> appConfigs() {
            return Map.of("adapter.key", "value");
        }

        @Override
        public Map<String, Object> appConfigsWithPrefix(String prefix) {
            return Map.of("key", "value");
        }
    }
}
