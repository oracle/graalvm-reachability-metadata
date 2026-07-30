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
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamsDeepWindowCoverageTest {

    @Test
    void buildsSessionAndSlidingWindowTopologiesThroughThePublicDsl() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> events = builder.stream("events", Consumed.with(Serdes.String(), Serdes.String()));
        events.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(5)))
                .count(Materialized.as("session-store"))
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                .toStream()
                .map((windowed, value) -> KeyValue.pair(windowed.key(), value.toString()))
                .to("session-output");
        events.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5)))
                .count(Materialized.<String, Long, WindowStore<org.apache.kafka.common.utils.Bytes, byte[]>>as("sliding-store"));
        events.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(5)))
                .reduce((left, right) -> right, Materialized.as("reduced-store"));

        Topology topology = builder.build();
        assertThat(topology.describe().subtopologies()).isNotEmpty();
    }

    @Test
    void buildsForeignKeyTableJoinAndMaterializedViewsThroughThePublicDsl() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> orders = builder.table("orders", Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("orders-store"));
        KTable<String, String> customers = builder.table("customers", Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("customers-store"));
        orders.join(customers, value -> value, (order, customer) -> order + ":" + customer,
                        Materialized.as("foreign-join-store"))
                .toStream()
                .to("foreign-join-output");
        orders.groupBy((key, value) -> KeyValue.pair(value, key), Grouped.with(Serdes.String(), Serdes.String()))
                .reduce((left, right) -> right, (left, right) -> left, Materialized.as("table-group-store"));

        Topology topology = builder.build();
        assertThat(topology.describe().subtopologies()).isNotEmpty();
    }
}
