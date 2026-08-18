/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the named and configured KStream overloads as one topology-building scenario. */
public class AdvancedKStreamApiCoverageTest {
    @Test
    @SuppressWarnings("deprecation")
    void shouldBuildConfiguredStreamBranchesJoinsAndProcessors() {
        StreamsBuilder builder = new StreamsBuilder();
        for (String storeName : List.of("store-a", "store-b", "store-c", "store-d", "store-e", "store-f", "store-g", "store-h")) {
            builder.addStateStore(org.apache.kafka.streams.state.Stores.keyValueStoreBuilder(
                    org.apache.kafka.streams.state.Stores.inMemoryKeyValueStore(storeName), Serdes.String(), Serdes.String()));
        }
        KStream<String, String> source = builder.stream("advanced-stream-source",
                Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> other = builder.stream("advanced-stream-other",
                Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, Integer> table = builder.table("advanced-stream-table",
                Consumed.with(Serdes.String(), Serdes.Integer()));
        JoinWindows windows = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2));
        StreamJoined<String, String, String> streamJoined = StreamJoined.with(
                Serdes.String(), Serdes.String(), Serdes.String()).withName("configured-stream-join");

        assertThat(source.merge(other, Named.as("merge-named"))).isNotNull();
        assertThat(source.split()).isNotNull();
        assertThat(source.split(Named.as("split-named")).branch((key, value) -> true).noDefaultBranch()).containsKey("split-named1");
        assertThat(source.repartition(Repartitioned.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(source.through("advanced-through")).isNotNull();
        source.to("advanced-output");
        source.to("advanced-output-configured", Produced.with(Serdes.String(), Serdes.String()));
        source.to((key, value, context) -> "advanced-dynamic-output");
        source.to((key, value, context) -> "advanced-dynamic-output-configured",
                Produced.with(Serdes.String(), Serdes.String()));

        Materialized<String, String, org.apache.kafka.streams.state.KeyValueStore<Bytes, byte[]>> materialized =
                Materialized.with(Serdes.String(), Serdes.String());
        assertThat(source.toTable()).isNotNull();
        assertThat(source.toTable(Named.as("table-named"))).isNotNull();
        assertThat(source.toTable(materialized)).isNotNull();
        assertThat(source.toTable(Named.as("table-named-configured"), materialized)).isNotNull();
        assertThat(source.groupBy((key, value) -> key, Grouped.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(source.join(other, (key, left, right) -> left + right, windows, streamJoined)).isNotNull();
        assertThat(source.leftJoin(other, (key, left, right) -> String.valueOf(left) + right, windows,
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()).withName("configured-left-join"))).isNotNull();
        assertThat(source.outerJoin(other, (key, left, right) -> String.valueOf(left) + right, windows,
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()).withName("configured-outer-join"))).isNotNull();
        assertThat(source.join(table, (key, left, right) -> left.length() + right, Joined.with(
                Serdes.String(), Serdes.String(), Serdes.Integer()))).isNotNull();
        assertThat(source.leftJoin(table, (key, left, right) -> left.length() + right, Joined.with(
                Serdes.String(), Serdes.String(), Serdes.Integer()))).isNotNull();

        KStream<String, String> transformed = source.transform(new StringTransformerSupplier(), Named.as("transform-named"), "store-a");
        assertThat(transformed).isNotNull();
        assertThat(source.flatTransform(new FlatStringTransformerSupplier(), Named.as("flat-transform-named"), "store-b")).isNotNull();
        assertThat(source.transformValues(new StringValueTransformerSupplier(), Named.as("value-transform-named"), "store-c")).isNotNull();
        assertThat(source.transformValues(new KeyedValueTransformerSupplier(), "store-d")).isNotNull();
        assertThat(source.transformValues(new KeyedValueTransformerSupplier(), Named.as("keyed-value-transform"), "store-e")).isNotNull();
        assertThat(source.flatTransformValues(new StringFlatValueTransformerSupplier(), Named.as("flat-value-transform"), "store-f")).isNotNull();
        assertThat(source.flatTransformValues(new KeyedFlatValueTransformerSupplier(), "store-g")).isNotNull();
        assertThat(source.flatTransformValues(new KeyedFlatValueTransformerSupplier(), Named.as("keyed-flat-value-transform"), "store-h")).isNotNull();

        assertThat(builder.build().describe().toString()).contains("advanced-stream-source", "advanced-through");
    }

    private static final class StringTransformerSupplier implements TransformerSupplier<String, String, KeyValue<String, String>> {
        @Override public Transformer<String, String, KeyValue<String, String>> get() {
            return new StringTransformer();
        }
    }

    private static final class FlatStringTransformerSupplier implements TransformerSupplier<String, String, Iterable<KeyValue<String, String>>> {
        @Override public Transformer<String, String, Iterable<KeyValue<String, String>>> get() {
            return new FlatStringTransformer();
        }
    }

    private static final class StringTransformer implements Transformer<String, String, KeyValue<String, String>> {
        @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
        @Override public KeyValue<String, String> transform(String key, String value) {
            return KeyValue.pair(key, value);
        }
        @Override public void close() { }
    }

    private static final class FlatStringTransformer implements Transformer<String, String, Iterable<KeyValue<String, String>>> {
        @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
        @Override public Iterable<KeyValue<String, String>> transform(String key, String value) {
            return List.of(KeyValue.pair(key, value));
        }
        @Override public void close() { }
    }

    private static final class StringValueTransformerSupplier implements ValueTransformerSupplier<String, String> {
        @Override public ValueTransformer<String, String> get() {
            return new StringValueTransformer();
        }
    }

    private static final class StringValueTransformer implements ValueTransformer<String, String> {
        @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
        @Override public String transform(String value) {
            return value.toUpperCase();
        }
        @Override public void close() { }
    }

    private static final class KeyedValueTransformerSupplier implements ValueTransformerWithKeySupplier<String, String, String> {
        @Override public ValueTransformerWithKey<String, String, String> get() {
            return new KeyedValueTransformer();
        }
    }

    private static final class KeyedValueTransformer implements ValueTransformerWithKey<String, String, String> {
        @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
        @Override public String transform(String readOnlyKey, String value) {
            return readOnlyKey + value;
        }
        @Override public void close() { }
    }

    private static final class StringFlatValueTransformerSupplier implements ValueTransformerSupplier<String, Iterable<String>> {
        @Override public ValueTransformer<String, Iterable<String>> get() {
            return new StringFlatValueTransformer();
        }
    }

    private static final class StringFlatValueTransformer implements ValueTransformer<String, Iterable<String>> {
        @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
        @Override public Iterable<String> transform(String value) {
            return List.of(value, value + "-copy");
        }
        @Override public void close() { }
    }

    private static final class KeyedFlatValueTransformerSupplier implements ValueTransformerWithKeySupplier<String, String, Iterable<String>> {
        @Override public ValueTransformerWithKey<String, String, Iterable<String>> get() {
            return new KeyedFlatValueTransformer();
        }
    }

    private static final class KeyedFlatValueTransformer implements ValueTransformerWithKey<String, String, Iterable<String>> {
        @Override public void init(org.apache.kafka.streams.processor.ProcessorContext context) { }
        @Override public Iterable<String> transform(String key, String value) {
            return List.of(key + value);
        }
        @Override public void close() { }
    }
}
