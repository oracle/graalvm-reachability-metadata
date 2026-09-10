/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.apache.kafka.streams.processor.StreamPartitioner;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.regex.Pattern;
import org.apache.kafka.streams.StreamsConfig;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the low-level topology construction API with real node wiring. */
public class TopologyApiCoverageTest {
    private static final Serde<String> SERDE = Serdes.String();
    private static final TimestampExtractor TIMESTAMP = (record, partitionTime) -> record.timestamp();
    private static final ProcessorSupplier<String, String> PROCESSOR = () -> new NoopProcessor();
    private static final StreamPartitioner<String, String> PARTITIONER = (topic, key, value, partitions) -> 0;
    private static final TopicNameExtractor<String, String> TOPIC = (key, value, context) -> "api-dynamic-output";

    @Test
    void shouldBuildSourcesProcessorsSinksAndStores() {
        Topology topology = new Topology(new TopologyConfig("api-topology", streamsConfig(), new Properties()));
        topology.addSource("source-topics", "api-input");
        topology.addSource("source-pattern", Pattern.compile("pattern-.*"));
        topology.addSource("source-deserialized", SERDE.deserializer(), SERDE.deserializer(), "api-deserialized");
        topology.addSource("source-pattern-deserialized", SERDE.deserializer(), SERDE.deserializer(), Pattern.compile("api-deserialized-.*"));
        topology.addSource(Topology.AutoOffsetReset.EARLIEST, "source-earliest", "api-earliest");
        topology.addSource(Topology.AutoOffsetReset.LATEST, "source-latest-pattern", Pattern.compile("pat-latest-.*"));
        topology.addSource(Topology.AutoOffsetReset.EARLIEST, "source-earliest-deserialized", SERDE.deserializer(), SERDE.deserializer(), "api-earliest-deserialized");
        topology.addSource(Topology.AutoOffsetReset.LATEST, "source-latest-pattern-deserialized", SERDE.deserializer(), SERDE.deserializer(), Pattern.compile("pat-latest-deserialized-.*"));
        topology.addSource(Topology.AutoOffsetReset.EARLIEST, TIMESTAMP, "source-timestamp", "api-timestamp");
        topology.addSource(Topology.AutoOffsetReset.LATEST, TIMESTAMP, "source-timestamp-pattern", Pattern.compile("pat-timestamp-.*"));
        topology.addSource(Topology.AutoOffsetReset.EARLIEST, "source-timestamp-deserialized", TIMESTAMP, SERDE.deserializer(), SERDE.deserializer(), "api-timestamp-deserialized");
        topology.addSource(Topology.AutoOffsetReset.LATEST, "source-timestamp-pattern-deserialized", TIMESTAMP, SERDE.deserializer(), SERDE.deserializer(), Pattern.compile("pat-timestamp-deserialized-.*"));
        topology.addSource(TIMESTAMP, "source-no-reset", "api-no-reset");
        topology.addSource(TIMESTAMP, "source-no-reset-pattern", Pattern.compile("pat-no-reset-.*"));

        topology.addProcessor("processor-old", PROCESSOR, "source-topics");
        topology.addProcessor("processor-old-second", PROCESSOR, "source-pattern");
        topology.addProcessor("processor-api", (org.apache.kafka.streams.processor.api.ProcessorSupplier<String, String, Void, Void>) new ApiProcessorSupplier(), "source-deserialized");

        topology.addStateStore(store("state-one"), "processor-old");
        topology.connectProcessorAndStateStores("processor-old", "state-one");

        topology.addSink("sink-topic", "api-sink", "processor-old");
        topology.addSink("sink-topic-serde", "api-sink-serde", SERDE.serializer(), SERDE.serializer(), "processor-old-second");
        topology.addSink("sink-topic-partitioner", "api-sink-partitioner", SERDE.serializer(), SERDE.serializer(), PARTITIONER, "processor-old-second");
        topology.addSink("sink-partitioner", "api-sink-partitioner-2", PARTITIONER, "processor-old-second");
        topology.addSink("sink-extractor", TOPIC, "processor-old-second");
        topology.addSink("sink-extractor-serde", TOPIC, SERDE.serializer(), SERDE.serializer(), "processor-old-second");
        topology.addSink("sink-extractor-partitioner", TOPIC, SERDE.serializer(), SERDE.serializer(), PARTITIONER, "processor-old-second");
        topology.addSink("sink-extractor-partitioner-2", TOPIC, PARTITIONER, "processor-old-second");

        assertThat(topology.describe().toString()).contains("source-topics", "processor-old", "api-sink");
    }

    @Test
    void shouldBuildGlobalStoresAndUseConfigFactories() {
        Topology topology = new Topology();
        topology.addGlobalStore(store("global-one"), "global-source-one", SERDE.deserializer(), SERDE.deserializer(), "api-global-one", "api-global-processor-one", PROCESSOR);
        topology.addGlobalStore(store("global-two"), "global-source-two", TIMESTAMP, SERDE.deserializer(), SERDE.deserializer(), "api-global-two", "api-global-processor-two", PROCESSOR);
        topology.addGlobalStore(
                store("global-three"),
                "global-source-three",
                SERDE.deserializer(),
                SERDE.deserializer(),
                "api-global-three",
                "api-global-processor-three",
                (org.apache.kafka.streams.processor.api.ProcessorSupplier<String, String, Void, Void>) new ApiProcessorSupplier());
        topology.addGlobalStore(
                store("global-four"),
                "global-source-four",
                TIMESTAMP,
                SERDE.deserializer(),
                SERDE.deserializer(),
                "api-global-four",
                "api-global-processor-four",
                (org.apache.kafka.streams.processor.api.ProcessorSupplier<String, String, Void, Void>) new ApiProcessorSupplier());
        assertThat(topology.describe().toString()).contains("global-one", "global-four");
        assertThat(new TopologyConfig("named", streamsConfig(), new Properties()).isNamedTopology()).isTrue();
        assertThat(new TopologyConfig("plain", streamsConfig(), new Properties()).parseStoreType()).isEqualTo(Materialized.StoreType.ROCKS_DB);
    }

    private static StreamsConfig streamsConfig() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "topology-api");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        return new StreamsConfig(properties);
    }

    private static StoreBuilder<?> store(String name) {
        return Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(name), SERDE, SERDE).withLoggingDisabled();
    }

    private static final class ApiProcessorSupplier implements org.apache.kafka.streams.processor.api.ProcessorSupplier<String, String, Void, Void> {
        @Override public org.apache.kafka.streams.processor.api.Processor<String, String, Void, Void> get() {
            return new ApiProcessor();
        }
    }

    private static final class ApiProcessor implements org.apache.kafka.streams.processor.api.Processor<String, String, Void, Void> {
        @Override public void init(org.apache.kafka.streams.processor.api.ProcessorContext<Void, Void> context) { }
        @Override public void process(org.apache.kafka.streams.processor.api.Record<String, String> record) { }
    }

    private static final class NoopProcessor implements Processor<String, String> {
        @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
        @Override public void process(String key, String value) { }
        @Override public void close() { }
    }
}
