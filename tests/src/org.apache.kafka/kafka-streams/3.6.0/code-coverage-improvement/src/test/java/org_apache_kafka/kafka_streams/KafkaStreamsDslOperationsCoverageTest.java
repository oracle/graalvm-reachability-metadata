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
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamsDslOperationsCoverageTest {

    @Test
    void buildsNamedStreamTransformationsAndAggregationTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> orders = builder.stream("orders", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> payments = builder.stream("payments");
        GlobalKTable<String, String> customers = builder.globalTable("customers", Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("customers-store"));
        KeyValueMapper<String, String, Iterable<KeyValue<String, String>>> duplicate =
                (key, value) -> java.util.List.of(KeyValue.pair(key, value + value));
        ValueJoiner<String, String, String> concatenate = (left, right) -> left + ":" + right;

        orders.filterNot((key, value) -> value.isBlank());
        orders.filterNot((key, value) -> key.isBlank(), Named.as("non-empty-key"));
        orders.flatMap(duplicate);
        orders.flatMap(duplicate, Named.as("duplicate-order"));
        orders.flatMapValues(value -> java.util.List.of(value, value.toUpperCase()));
        orders.flatMapValues(value -> java.util.List.of(value), Named.as("value-copy"));
        orders.flatMapValues((key, value) -> java.util.List.of(key + value));
        orders.flatMapValues((key, value) -> java.util.List.of(value), Named.as("keyed-value-copy"));
        orders.foreach((key, value) -> { });
        orders.foreach((key, value) -> { }, Named.as("audit-orders"));
        orders.groupBy((key, value) -> key).count();
        orders.groupBy((key, value) -> key, Grouped.with(Serdes.String(), Serdes.String())).count();
        orders.groupByKey().count();
        orders.groupByKey(Grouped.with(Serdes.String(), Serdes.String())).count();
        Map<String, KStream<String, String>> branches = orders.split(Named.as("order-"))
                .branch((key, value) -> value.startsWith("priority"), Branched.as("priority"))
                .defaultBranch(Branched.as("standard"));
        assertThat(branches).containsKeys("order-priority", "order-standard");

        orders.join(customers, (key, value) -> key, concatenate);
        orders.join(customers, (key, value) -> key, concatenate, Named.as("customer-join"));
        orders.join(customers, (key, value) -> key, (key, left, right) -> key + left + right);
        orders.join(customers, (key, value) -> key, (key, left, right) -> key + left + right, Named.as("keyed-customer-join"));
        orders.join(payments, concatenate, JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5)));
        orders.join(payments, concatenate, JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5)),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()));

        Topology topology = builder.build();
        assertThat(topology.describe().subtopologies()).isNotEmpty();
        assertThat(topology.describe().globalStores()).hasSize(1);
    }

}
