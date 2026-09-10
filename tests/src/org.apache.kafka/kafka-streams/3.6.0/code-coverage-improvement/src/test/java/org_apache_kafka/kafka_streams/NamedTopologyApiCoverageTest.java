/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.internals.namedtopology.KafkaStreamsNamedTopologyWrapper;
import org.apache.kafka.streams.processor.internals.namedtopology.NamedTopology;
import org.apache.kafka.streams.processor.internals.namedtopology.NamedTopologyBuilder;
import org.apache.kafka.streams.processor.internals.namedtopology.NamedTopologyStoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Builds and inspects a named topology through the public named-topology facade. */
public class NamedTopologyApiCoverageTest {
    @Test
    void shouldBuildNamedTopologyAndReportPreStartQueryFailures() {
        KafkaStreamsNamedTopologyWrapper streams = new KafkaStreamsNamedTopologyWrapper(properties());
        try {
            NamedTopologyBuilder builder = streams.newNamedTopologyBuilder("orders");
            builder.stream("named-orders", org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), Serdes.String()))
                    .to("named-orders-output");
            NamedTopology topology = builder.build();
            assertThat(topology).isNotNull();
            assertThat(streams.getFullTopologyDescription()).isEmpty();
            assertThat(streams.getAllTopologies()).isEmpty();
            assertThat(streams.newNamedTopologyBuilder("empty").build()).isNotNull();
            streams.start(topology);
            assertThat(streams.getFullTopologyDescription()).contains("orders");
            assertThat(streams.getAllTopologies()).extracting(NamedTopology::name).contains("orders");
            assertThatThrownBy(() -> streams.store(NamedTopologyStoreQueryParameters
                    .fromNamedTopologyAndStoreNameAndType("orders", "missing", QueryableStoreTypes.keyValueStore())))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> streams.queryMetadataForKey("missing", "key", Serdes.String().serializer(), "missing"))
                    .isInstanceOf(RuntimeException.class);
            assertThat(streams.allStreamsClientsMetadataForTopology("orders")).isNotNull();
            assertThatThrownBy(() -> streams.streamsMetadataForStore("missing", "orders"))
                    .isInstanceOf(RuntimeException.class);
            assertThat(streams.allLocalStorePartitionLagsForTopology("orders")).isNotNull();
            streams.pauseNamedTopology("orders");
            assertThat(streams.isNamedTopologyPaused("orders")).isTrue();
            streams.resumeNamedTopology("orders");
            assertThat(streams.isNamedTopologyPaused("orders")).isFalse();
            Collection<NamedTopology> topologies = streams.getAllTopologies();
            assertThat(topologies).hasSize(1);
            assertThat(streams.removeNamedTopology("orders")).isNotNull();
            assertThat(streams.getAllTopologies()).isEmpty();
            streams.cleanUpNamedTopology("orders");
        } finally {
            streams.close(java.time.Duration.ZERO);
        }
    }

    private static Properties properties() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "named-api");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        return properties;
    }
}
