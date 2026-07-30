/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.CogroupedKStream;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.TableJoined;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.query.StateQueryResult;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.internals.Maybe;
import org.apache.kafka.streams.state.internals.Murmur3;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamsApiLoopCoverageTest {

    @Test
    void constructsKafkaStreamsWithAnExplicitClientSupplier() {
        Topology topology = new Topology().addSource("source", "orders").addSink("sink", "processed-orders", "source");
        java.util.Properties properties = new java.util.Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "explicit-supplier-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, System.getProperty("java.io.tmpdir") + "/explicit-supplier-coverage");

        try (KafkaStreams streams = new KafkaStreams(topology, properties,
                new org.apache.kafka.streams.processor.internals.DefaultKafkaClientSupplier(),
                org.apache.kafka.common.utils.Time.SYSTEM)) {
            assertThat(streams.state()).isEqualTo(KafkaStreams.State.CREATED);
            assertThat(streams.metrics()).isNotEmpty();
        }
    }

    @Test
    void buildsTableJoinAndValueTransformationTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> orders = builder.table("orders", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> customers = builder.table("customers", Consumed.with(Serdes.String(), Serdes.String()));

        assertThat(orders.filter((key, value) -> value.startsWith("paid"))).isNotNull();
        assertThat(orders.mapValues(String::length)).isNotNull();
        assertThat(orders.join(customers, (order, customer) -> order + ":" + customer)).isNotNull();
        assertThat(orders.leftJoin(customers, String::toUpperCase, (order, customer) -> order + ":" + customer,
                TableJoined.as("customer-lookup"), Materialized.as("joined-orders"))).isNotNull();

        Topology topology = builder.build();
        assertThat(topology.describe().subtopologies()).isNotEmpty();
        assertThat(topology.describe().toString()).contains("joined-orders");
    }

    @Test
    void buildsStreamJoinRepartitionAndObservationTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> orders = builder.stream("orders", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> payments = builder.stream("payments", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> enriched = orders.leftJoin(payments, (order, payment) -> order + ":" + payment,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5)),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()));
        enriched.peek((key, value) -> assertThat(value).contains(":"));
        enriched.print(Printed.toSysOut());
        assertThat(enriched.repartition().merge(orders)).isNotNull();

        Topology topology = builder.build();
        assertThat(topology.describe().subtopologies()).isNotEmpty();
    }

    @Test
    void addsGlobalStoresUsingTimestampedAndDefaultSourceForms() {
        org.apache.kafka.streams.processor.ProcessorSupplier<String, String> legacyProcessor =
                () -> new org.apache.kafka.streams.processor.AbstractProcessor<>() {
                    @Override
                    public void process(String key, String value) {
                        assertThat(key).isNotBlank();
                        assertThat(value).isNotBlank();
                    }
                };
        org.apache.kafka.streams.processor.api.ProcessorSupplier<String, String, Void, Void> apiProcessor =
                () -> new org.apache.kafka.streams.processor.api.ContextualProcessor<>() {
                    @Override
                    public void process(org.apache.kafka.streams.processor.api.Record<String, String> record) {
                        assertThat(record.key()).isNotBlank();
                        assertThat(record.value()).isNotBlank();
                    }
                };
        Topology topology = new Topology()
                .addGlobalStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("legacy-global"),
                        Serdes.String(), Serdes.String()).withLoggingDisabled(), "legacy-source", Serdes.String().deserializer(),
                        Serdes.String().deserializer(), "legacy-topic", "legacy-processor", legacyProcessor)
                .addGlobalStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("api-global"),
                        Serdes.String(), Serdes.String()).withLoggingDisabled(), "api-source", (record, previous) -> record.timestamp(),
                        Serdes.String().deserializer(), Serdes.String().deserializer(), "api-topic", "api-processor", apiProcessor);

        assertThat(topology.describe().globalStores()).hasSize(2);
        assertThat(topology.describe().toString()).contains("legacy-global").contains("api-global");
    }

    @Test
    void buildsLegacyTransformForeignKeyAndWindowedCogroupOperators() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> orders = builder.stream("orders", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> payments = builder.stream("payments", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> orderTable = builder.table("order-table", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> customerTable = builder.table("customer-table", Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> transformed = orders.transformValues(() -> valueTransformer("-checked"));
        transformed.flatTransformValues(() -> valueTransformerList("-audit"));
        orders.outerJoin(payments, (order, payment) -> order + ":" + payment,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5)));
        orders.process(() -> new org.apache.kafka.streams.processor.AbstractProcessor<String, String>() {
            @Override
            public void process(String key, String value) {
                assertThat(key).isNotBlank();
                assertThat(value).isNotBlank();
            }
        });

        orderTable.join(customerTable, (order, customer) -> order + ":" + customer, Materialized.as("table-join"));
        orderTable.transformValues(() -> keyedValueTransformer("-normalized"));
        KGroupedStream<String, String> groupedOrders = orders.groupByKey();
        CogroupedKStream<String, String> cogrouped = groupedOrders.cogroup((key, value, aggregate) -> aggregate + value);
        cogrouped.aggregate(() -> "");
        cogrouped.windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10))).aggregate(() -> "");
        cogrouped.windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10))).aggregate(() -> "");
        cogrouped.windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(10)))
                .aggregate(() -> "", (key, left, right) -> left + right);
        groupedOrders.windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10))).reduce((left, right) -> left + right);
        groupedOrders.windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10))).count();
        groupedOrders.windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(10))).reduce((left, right) -> left + right);

        Topology topology = builder.build();
        assertThat(topology.describe().subtopologies()).isNotEmpty();
        assertThat(topology.describe().toString()).contains("table-join");
    }

    @Test
    void exposesStoreSuppliersHashingAndQueryResultContracts() {
        assertThat(Stores.lruMap("recent-orders", 25).name()).isEqualTo("recent-orders");
        assertThat(Stores.persistentVersionedKeyValueStore("versions", Duration.ofHours(1)).name()).isEqualTo("versions");
        assertThat(QueryableStoreTypes.sessionStore()).isNotNull();

        byte[] orderId = "order-17".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(Murmur3.hash32(orderId)).isEqualTo(Murmur3.hash32(orderId));
        assertThat(Maybe.defined("paid").hashCode()).isEqualTo(Maybe.defined("paid").hashCode());
        assertThat(Maybe.undefined().hashCode()).isEqualTo(Maybe.undefined().hashCode());

        StateQueryResult<String> result = new StateQueryResult<>();
        assertThat(result.getOnlyPartitionResult()).isNull();
        assertThat(result.getPartitionResults()).isEmpty();
    }

    private ValueTransformer<String, String> valueTransformer(String suffix) {
        return new ValueTransformer<>() {
            @Override
            public void init(org.apache.kafka.streams.processor.ProcessorContext context) {
            }

            @Override
            public String transform(String value) {
                return value + suffix;
            }

            @Override
            public void close() {
            }
        };
    }

    private ValueTransformer<String, Iterable<String>> valueTransformerList(String suffix) {
        return new ValueTransformer<>() {
            @Override
            public void init(org.apache.kafka.streams.processor.ProcessorContext context) {
            }

            @Override
            public Iterable<String> transform(String value) {
                return java.util.List.of(value, value + suffix);
            }

            @Override
            public void close() {
            }
        };
    }

    private ValueTransformerWithKey<String, String, String> keyedValueTransformer(String suffix) {
        return new ValueTransformerWithKey<>() {
            @Override
            public void init(org.apache.kafka.streams.processor.ProcessorContext context) {
            }

            @Override
            public String transform(String key, String value) {
                return key + ":" + value + suffix;
            }

            @Override
            public void close() {
            }
        };
    }
}
