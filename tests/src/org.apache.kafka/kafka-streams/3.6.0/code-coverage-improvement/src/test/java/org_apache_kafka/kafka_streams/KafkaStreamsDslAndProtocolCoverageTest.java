/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.DefaultProductionExceptionHandler;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.apache.kafka.streams.internals.generated.SubscriptionInfoData;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.EmitStrategy;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.apache.kafka.test.NoOpProcessorContext;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamsDslAndProtocolCoverageTest {

    @Test
    void configuresDslOptionsWithoutLosingTheirStreamSemantics() {
        TimestampExtractor timestamps = (record, previousTimestamp) -> 42L;
        Consumed<String, String> consumed = Consumed.<String, String>as("orders-source")
                .withKeySerde(Serdes.String())
                .withValueSerde(Serdes.String())
                .withTimestampExtractor(timestamps)
                .withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST)
                .withName("renamed-source");
        StreamsBuilder sourceBuilder = new StreamsBuilder();
        sourceBuilder.stream("orders", consumed);
        assertThat(sourceBuilder.build().describe().subtopologies()).hasSize(1);
        assertThat(Consumed.with(timestamps)).isNotNull();
        assertThat(Consumed.with(Topology.AutoOffsetReset.LATEST)).isNotNull();

        assertThat(Grouped.<String, Long>as("totals").withKeySerde(Serdes.String())
                .withValueSerde(Serdes.Long()).withName("daily-totals")).isNotNull();
        assertThat(Grouped.keySerde(Serdes.String())).isNotNull();
        assertThat(Grouped.valueSerde(Serdes.Long())).isNotNull();
        assertThat(Grouped.with("named", Serdes.String(), Serdes.Long())).isNotNull();

        assertThat(Branched.<String, String>as("valid").withName("accepted")).isNotNull();
        assertThat(Branched.<String, String>withConsumer(stream -> { }).withName("audit")).isNotNull();
        assertThat(Branched.<String, String>withConsumer(stream -> { }, "dead-letter")).isNotNull();
        assertThat(Branched.<String, String>withFunction(stream -> stream, "normalized")).isNotNull();
        assertThat(Branched.<String, String>withFunction(stream -> stream).withName("enriched")).isNotNull();
        assertThat(EmitStrategy.onWindowClose()).isNotEqualTo(EmitStrategy.onWindowUpdate());
        assertThat(JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))
                .before(Duration.ofSeconds(2)).after(Duration.ofSeconds(3)).size()).isEqualTo(5000L);
    }

    @Test
    void describesTopologiesBuiltWithPublicSourceAndSinkForms() {
        TimestampExtractor timestamps = (record, previousTimestamp) -> previousTimestamp + 1;
        Topology.AutoOffsetReset reset = Topology.AutoOffsetReset.EARLIEST;

        assertTopology(new Topology().addSource("pattern", Pattern.compile("orders-.*")));
        assertTopology(new Topology().addSource("serde", Serdes.String().deserializer(),
                Serdes.String().deserializer(), "orders"));
        assertTopology(new Topology().addSource("serde-pattern", Serdes.String().deserializer(),
                Serdes.String().deserializer(), Pattern.compile("orders-.*")));
        assertTopology(new Topology().addSource(reset, "reset", "orders"));
        assertTopology(new Topology().addSource(reset, "reset-pattern", Pattern.compile("orders-.*")));
        assertTopology(new Topology().addSource(reset, "reset-serde", Serdes.String().deserializer(),
                Serdes.String().deserializer(), "orders"));
        assertTopology(new Topology().addSource(reset, "reset-serde-pattern", Serdes.String().deserializer(),
                Serdes.String().deserializer(), Pattern.compile("orders-.*")));
        assertTopology(new Topology().addSource(reset, "timestamped", timestamps,
                Serdes.String().deserializer(), Serdes.String().deserializer(), "orders"));
        assertTopology(new Topology().addSource(reset, "timestamped-pattern", timestamps,
                Serdes.String().deserializer(), Serdes.String().deserializer(), Pattern.compile("orders-.*")));
        assertTopology(new Topology().addSource(reset, timestamps, "legacy-timestamp", "orders"));
        assertTopology(new Topology().addSource(reset, timestamps, "legacy-timestamp-pattern",
                Pattern.compile("orders-.*")));
        assertTopology(new Topology().addSource(timestamps, "timestamp", "orders"));
        assertTopology(new Topology().addSource(timestamps, "timestamp-pattern", Pattern.compile("orders-.*")));

        assertTopology(new Topology().addSource("source", "orders")
                .addSink("sink-serde", "output", Serdes.String().serializer(), Serdes.String().serializer(), "source"));
        assertTopology(new Topology().addSource("source", "orders")
                .addSink("sink-partition", "output", (topic, key, value, partitions) -> 0, "source"));
        assertTopology(new Topology().addSource("source", "orders")
                .addSink("sink-both", "output", Serdes.String().serializer(), Serdes.String().serializer(),
                        (topic, key, value, partitions) -> 0, "source"));
        assertTopology(new Topology().addSource("source", "orders")
                .addSink("dynamic", (key, value, context) -> "output", "source"));
        assertTopology(new Topology().addSource("source", "orders")
                .addSink("dynamic-partition", (key, value, context) -> "output",
                        (topic, key, value, partitions) -> 0, "source"));
        assertTopology(new Topology().addSource("source", "orders")
                .addSink("dynamic-serde", (key, value, context) -> "output", Serdes.String().serializer(),
                        Serdes.String().serializer(), "source"));
        assertTopology(new Topology().addSource("source", "orders")
                .addSink("dynamic-all", (key, value, context) -> "output", Serdes.String().serializer(),
                        Serdes.String().serializer(), (topic, key, value, partitions) -> 0, "source"));

        Topology stateTopology = new Topology().addSource("source", "orders")
                .addProcessor("counter", () -> new AbstractProcessor<String, String>() {
                    @Override
                    public void process(String key, String value) {
                    }
                }, "source")
                .addStateStore(Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore("counts"),
                        Serdes.String(), Serdes.Long()), "counter")
                .connectProcessorAndStateStores("counter", "counts");
        assertThat(stateTopology.describe().subtopologies()).hasSize(1);
    }

    @Test
    void preservesProtocolPayloadsAndHandlerOutcomes() {
        SubscriptionInfoData.TaskId task = new SubscriptionInfoData.TaskId().setTopicGroupId(4).setPartition(2);
        SubscriptionInfoData.PartitionToOffsetSum partition = new SubscriptionInfoData.PartitionToOffsetSum()
                .setPartition(2).setOffsetSum(19L);
        SubscriptionInfoData.TaskOffsetSum offset = new SubscriptionInfoData.TaskOffsetSum()
                .setTopicGroupId(4).setPartition(2).setOffsetSum(19L).setNamedTopology("orders")
                .setPartitionToOffsetSum(List.of(partition));
        SubscriptionInfoData.ClientTag tag = new SubscriptionInfoData.ClientTag()
                .setKey(new byte[] {1}).setValue(new byte[] {2, 3});
        SubscriptionInfoData data = new SubscriptionInfoData().setPrevTasks(List.of(task)).setStandbyTasks(List.of(task));

        assertThat(data.prevTasks()).containsExactly(task);
        assertThat(data.standbyTasks()).containsExactly(task);
        assertThat(data.duplicate()).isEqualTo(data);
        assertThat(data.toString()).contains("SubscriptionInfoData");
        assertThat(task.duplicate()).isEqualTo(task);
        assertThat(partition.duplicate()).isEqualTo(partition);
        assertThat(offset.duplicate()).isEqualTo(offset);
        assertThat(offset.partitionToOffsetSum()).containsExactly(partition);
        assertThat(tag.duplicate()).isEqualTo(tag);
        assertThat(tag.key()).containsExactly(1);
        assertThat(tag.value()).containsExactly(2, 3);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("orders", 0, 0L, new byte[] {1}, new byte[] {2});
        NoOpProcessorContext context = new NoOpProcessorContext();
        assertThat(new LogAndContinueExceptionHandler().handle(context, record, new IllegalArgumentException()))
                .isEqualTo(DeserializationExceptionHandler.DeserializationHandlerResponse.CONTINUE);
        new LogAndContinueExceptionHandler().configure(Map.of());
        assertThat(new LogAndFailExceptionHandler().handle(context, record, new IllegalArgumentException()))
                .isEqualTo(DeserializationExceptionHandler.DeserializationHandlerResponse.FAIL);
        assertThat(new DefaultProductionExceptionHandler().handle(new ProducerRecord<>("orders", new byte[] {1}, new byte[] {2}),
                new IllegalStateException())).isEqualTo(ProductionExceptionHandler.ProductionExceptionHandlerResponse.FAIL);
    }

    private void assertTopology(Topology topology) {
        assertThat(topology.describe().subtopologies()).hasSize(1);
    }
}
