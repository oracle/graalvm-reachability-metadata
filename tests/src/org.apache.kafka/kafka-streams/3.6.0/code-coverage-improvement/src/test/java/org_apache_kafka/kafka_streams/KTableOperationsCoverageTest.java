/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.TableJoined;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KTableOperationsCoverageTest {
    @Test
    @SuppressWarnings("deprecation")
    void buildsAllPublicFilteringMappingAndTransformationForms() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> source = builder.table("table-input", Materialized.as("source-store"));
        List<KTable<?, ?>> tables = new ArrayList<>();

        tables.add(source.filter((key, value) -> !value.isBlank()));
        tables.add(source.filter((key, value) -> !value.isBlank(), Named.as("filter-named")));
        tables.add(source.filterNot((key, value) -> value.isBlank()));
        tables.add(source.filterNot((key, value) -> value.isBlank(), Named.as("filter-not-named")));
        tables.add(source.mapValues(String::length));
        tables.add(source.mapValues(String::length, Named.as("map-named")));
        tables.add(source.mapValues((key, value) -> key.length() + value.length()));
        tables.add(source.mapValues((key, value) -> key.length() + value.length(), Named.as("map-key-named")));
        tables.add(source.mapValues((key, value) -> key + value, Materialized.as("map-materialized")));
        tables.add(source.mapValues((key, value) -> key + value, Named.as("map-all-named"),
                Materialized.as("map-all-materialized")));

        ValueTransformerWithKeySupplier<String, String, String> transformer = () ->
                new ValueTransformerWithKey<>() {
                    @Override
                    public void init(ProcessorContext context) { }

                    @Override
                    public String transform(String readOnlyKey, String value) {
                        return readOnlyKey + ':' + value;
                    }

                    @Override
                    public void close() { }
                };
        tables.add(source.transformValues(transformer));
        tables.add(source.transformValues(transformer, Named.as("transform-named")));
        tables.add(source.transformValues(transformer, Materialized.as("transform-materialized")));
        tables.add(source.transformValues(transformer, Materialized.as("transform-all-materialized"),
                Named.as("transform-all-named")));
        source.toStream((key, value) -> key + value, Named.as("table-to-stream")).to("table-output");

        Topology topology = builder.build();
        assertThat(tables).hasSize(14).allSatisfy(table -> assertThat(table).isNotNull());
        assertThat(source.queryableStoreName()).isEqualTo("source-store");
        assertThat(topology.describe().toString())
                .contains("filter-named", "map-all-named", "transform-all-named", "table-to-stream");
    }

    @Test
    void buildsEveryPrimaryKeyTableJoinForm() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> left = builder.table("primary-left", Materialized.as("primary-left-store"));
        KTable<String, String> right = builder.table("primary-right", Materialized.as("primary-right-store"));
        List<KTable<String, String>> joins = List.of(
                left.join(right, (a, b) -> a + b),
                left.join(right, (a, b) -> a + b, Named.as("primary-inner-named")),
                left.leftJoin(right, (a, b) -> a + b),
                left.leftJoin(right, (a, b) -> a + b, Named.as("primary-left-named")),
                left.outerJoin(right, (a, b) -> String.valueOf(a) + b),
                left.outerJoin(right, (a, b) -> String.valueOf(a) + b, Named.as("primary-outer-named")));

        assertThat(joins).hasSize(6).doesNotContainNull();
        assertThat(builder.build().describe().toString())
                .contains("primary-inner-named", "primary-left-named", "primary-outer-named");
    }

    @Test
    void buildsEveryForeignKeyTableJoinForm() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> orders = builder.table("orders", Materialized.as("orders-store"));
        KTable<String, String> customers = builder.table("customers", Materialized.as("customers-store"));
        List<KTable<String, String>> joins = List.of(
                orders.join(customers, value -> value, (order, customer) -> order + customer),
                orders.join(customers, value -> value, (order, customer) -> order + customer,
                        Named.as("foreign-inner-named")),
                orders.join(customers, value -> value, (order, customer) -> order + customer,
                        TableJoined.as("foreign-inner-table-joined")),
                orders.join(customers, value -> value, (order, customer) -> order + customer,
                        Materialized.as("foreign-inner-materialized")),
                orders.join(customers, value -> value, (order, customer) -> order + customer,
                        Named.as("foreign-inner-all-named"), Materialized.as("foreign-inner-all-store")),
                orders.join(customers, value -> value, (order, customer) -> order + customer,
                        TableJoined.as("foreign-inner-all-table-joined"), Materialized.as("foreign-inner-all-store-2")),
                orders.leftJoin(customers, value -> value, (order, customer) -> order + customer),
                orders.leftJoin(customers, value -> value, (order, customer) -> order + customer,
                        Named.as("foreign-left-named")),
                orders.leftJoin(customers, value -> value, (order, customer) -> order + customer,
                        TableJoined.as("foreign-left-table-joined")),
                orders.leftJoin(customers, value -> value, (order, customer) -> order + customer,
                        Materialized.as("foreign-left-materialized")),
                orders.leftJoin(customers, value -> value, (order, customer) -> order + customer,
                        Named.as("foreign-left-all-named"), Materialized.as("foreign-left-all-store")),
                orders.leftJoin(customers, value -> value, (order, customer) -> order + customer,
                        TableJoined.as("foreign-left-all-table-joined"), Materialized.as("foreign-left-all-store-2")));

        assertThat(joins).hasSize(12).doesNotContainNull();
        assertThat(builder.build().describe().toString())
                .contains("foreign-inner-named", "foreign-inner-table-joined",
                        "foreign-left-named", "foreign-left-table-joined");
    }
}
