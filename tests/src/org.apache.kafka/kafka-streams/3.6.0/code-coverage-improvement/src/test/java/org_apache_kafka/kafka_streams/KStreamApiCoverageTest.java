/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Builds realistic stream topologies through the public KStream API. */
public class KStreamApiCoverageTest {
    @Test
    @SuppressWarnings("deprecation")
    void shouldComposeStreamTransformationsAndSinks() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> input = builder.stream("api-stream-input", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> other = builder.stream("api-stream-other", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table = builder.table("api-stream-table", Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> filtered = input.filter((key, value) -> value != null, Named.as("filter"))
                .filterNot((key, value) -> value.isEmpty(), Named.as("filter-not"));
        KStream<String, Integer> mapped = filtered
                .map((key, value) -> KeyValue.pair(key, value.length()), Named.as("map"))
                .mapValues(Integer::intValue, Named.as("map-values"));
        KStream<String, Integer> expanded = mapped
                .flatMap((key, value) -> List.of(KeyValue.pair(key, value), KeyValue.pair(key + "-copy", value)), Named.as("flat-map"))
                .flatMapValues(value -> List.of(value, value + 1), Named.as("flat-map-values"));
        expanded.peek((key, value) -> assertThat(value).isPositive(), Named.as("peek"));
        expanded.foreach((key, value) -> assertThat(key).isNotNull(), Named.as("foreach"));
        expanded.print(Printed.<String, Integer>toSysOut().withLabel("api-stream"));

        KStream<String, String> strings = input.merge(other, Named.as("merge"));
        strings.repartition(Repartitioned.with(Serdes.String(), Serdes.String()).withName("repartition"));
        strings.through("api-stream-through", Produced.with(Serdes.String(), Serdes.String()));
        strings.to("api-stream-output", Produced.with(Serdes.String(), Serdes.String()));
        strings.to((key, value, recordContext) -> "api-stream-dynamic-output", Produced.with(Serdes.String(), Serdes.String()));
        strings.toTable(Named.as("to-table"), Materialized.with(Serdes.String(), Serdes.String()));
        strings.groupBy((key, value) -> key, Grouped.with(Serdes.String(), Serdes.String())).count();
        strings.groupByKey(Grouped.with(Serdes.String(), Serdes.String())).count();

        KStream<String, String>[] branches = strings.branch(
                Named.as("branches"), (key, value) -> value.startsWith("a"), (key, value) -> true);
        assertThat(branches).hasSize(2);
        assertThat(strings.split(Named.as("split")).branch((key, value) -> value.length() > 1).defaultBranch()).isNotNull();
        assertThat(strings).isNotNull();

        assertThat(input.join(other, (left, right) -> left + right, JoinWindows.ofTimeDifferenceWithNoGrace(java.time.Duration.ofSeconds(5)))).isNotNull();
        assertThat(input.leftJoin(other, (left, right) -> left + right, JoinWindows.ofTimeDifferenceWithNoGrace(java.time.Duration.ofSeconds(5)))).isNotNull();
        assertThat(input.outerJoin(other, (left, right) -> String.valueOf(left) + right, JoinWindows.ofTimeDifferenceWithNoGrace(java.time.Duration.ofSeconds(5)))).isNotNull();
        assertThat(input.join(table, (left, right) -> left + right)).isNotNull();
        assertThat(input.leftJoin(table, (left, right) -> left + right)).isNotNull();

        assertThat(builder.build()).isNotNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldExerciseGlobalJoinsAndRepartitionDefaults() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> stream = builder.stream("api-global-join-input", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> other = builder.stream("api-global-join-other", Consumed.with(Serdes.String(), Serdes.String()));
        org.apache.kafka.streams.kstream.GlobalKTable<String, String> global = builder.globalTable("api-global-join-table");
        assertThat(stream.join(other, (left, right) -> left + right,
                JoinWindows.ofTimeDifferenceWithNoGrace(java.time.Duration.ofSeconds(1)),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(stream.join(other, (left, right) -> left + right,
                JoinWindows.ofTimeDifferenceWithNoGrace(java.time.Duration.ofSeconds(1)),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(stream.join(global, (key, value) -> key, (left, right) -> left + right)).isNotNull();
        assertThat(stream.join(global, (key, value) -> key, (left, right) -> left + right, Named.as("global-join"))).isNotNull();
        assertThat(stream.leftJoin(global, (key, value) -> key, (left, right) -> String.valueOf(left) + right)).isNotNull();
        assertThat(stream.leftJoin(global, (key, value) -> key, (left, right) -> String.valueOf(left) + right, Named.as("global-left"))).isNotNull();
        assertThat(stream.groupBy((String key, String value) -> key,
                Grouped.<String, String>with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(stream.flatMapValues(value -> List.of(value)).repartition()).isNotNull();
        assertThat(stream.mapValues(value -> value.toUpperCase())).isNotNull();
        assertThat(builder.build().describe().toString()).contains("api-global-join");
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldCreateProcessorAndTransformerStages() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> input = builder.stream("api-process-input", Consumed.with(Serdes.String(), Serdes.String()));
        input.process(() -> new NoopProcessor());
        assertThat(input.process(new ApiProcessorSupplier())).isNotNull();
        assertThat(input.processValues(() -> new ApiFixedKeyProcessor())).isNotNull();
        assertThat(input.transform(() -> new ApiTransformer())).isNotNull();
        assertThat(input.transformValues(() -> new ApiValueTransformer())).isNotNull();
        assertThat(input.flatTransformValues(() -> new ApiFlatValueTransformer())).isNotNull();
        Topology topology = builder.build();
        assertThat(topology.describe().toString()).contains("api-process-input");
    }

    private static final class NoopProcessor implements Processor<String, String> {
        @Override public void init(ProcessorContext context) { }
        @Override public void process(String key, String value) { }
        @Override public void close() { }
    }

    private static final class ApiProcessorSupplier implements org.apache.kafka.streams.processor.api.ProcessorSupplier<String, String, String, String> {
        @Override public org.apache.kafka.streams.processor.api.Processor<String, String, String, String> get() {
            return new ApiProcessor();
        }
    }

    private static final class ApiProcessor implements org.apache.kafka.streams.processor.api.Processor<String, String, String, String> {
        @Override public void init(org.apache.kafka.streams.processor.api.ProcessorContext<String, String> context) { }
        @Override public void process(org.apache.kafka.streams.processor.api.Record<String, String> record) { }
    }

    private static final class ApiFixedKeyProcessor implements org.apache.kafka.streams.processor.api.FixedKeyProcessor<String, String, String> {
        @Override public void init(org.apache.kafka.streams.processor.api.FixedKeyProcessorContext<String, String> context) { }
        @Override public void process(org.apache.kafka.streams.processor.api.FixedKeyRecord<String, String> record) { }
    }

    private static final class ApiFlatValueTransformer implements ValueTransformer<String, Iterable<String>> {
        @Override public void init(ProcessorContext context) { }
        @Override public Iterable<String> transform(String value) {
            return List.of(value);
        }
        @Override public void close() { }
    }

    private static final class ApiValueTransformer implements ValueTransformer<String, String> {
        @Override public void init(ProcessorContext context) { }
        @Override public String transform(String value) {
            return value;
        }
        @Override public void close() { }
    }

    private static final class ApiTransformer implements Transformer<String, String, KeyValue<String, String>> {
        @Override public void init(ProcessorContext context) { }
        @Override public KeyValue<String, String> transform(String key, String value) {
            return KeyValue.pair(key, value);
        }
        @Override public void close() { }
    }
}
