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

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class NamedTopologyCoverageTest {
    @Test
    void buildsAndCatalogsIndependentNamedTopologies() {
        KafkaStreamsNamedTopologyWrapper streams = new KafkaStreamsNamedTopologyWrapper(configuration());
        try {
            NamedTopologyBuilder ordersBuilder = streams.newNamedTopologyBuilder("orders");
            ordersBuilder.stream("orders-input").to("orders-output");
            NamedTopology orders = ordersBuilder.build();

            NamedTopologyBuilder paymentsBuilder = streams.newNamedTopologyBuilder("payments", new Properties());
            paymentsBuilder.stream("payments-input").to("payments-output");
            NamedTopology payments = paymentsBuilder.build();

            assertThat(orders.name()).isEqualTo("orders");
            assertThat(payments.name()).isEqualTo("payments");
            assertThat(orders.describe().toString()).contains("orders-input", "orders-output");
            assertThat(streams.getAllTopologies()).isEmpty();
            assertThat(streams.getTopologyByName("orders")).isEmpty();
            assertThat(streams.getFullTopologyDescription()).isEmpty();
        } finally {
            streams.close();
            assertThat(streams.state()).isEqualTo(org.apache.kafka.streams.KafkaStreams.State.NOT_RUNNING);
        }
    }

    @Test
    void namedStoreQueryParametersRetainTopologyStoreAndRoutingOptions() {
        NamedTopologyStoreQueryParameters<?> parameters = NamedTopologyStoreQueryParameters
                .fromNamedTopologyAndStoreNameAndType("orders", "order-store",
                        QueryableStoreTypes.keyValueStore())
                .withPartition(4)
                .enableStaleStores();

        assertThat(parameters.topologyName()).isEqualTo("orders");
        assertThat(parameters.storeName()).isEqualTo("order-store");
        assertThat(parameters.partition()).isEqualTo(4);
        assertThat(parameters.staleStoresEnabled()).isTrue();
        assertThat(parameters).isEqualTo(parameters).hasSameHashCodeAs(parameters);
    }

    private static Properties configuration() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "named-topology-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.STATE_DIR_CONFIG,
                System.getProperty("java.io.tmpdir") + "/named-topology-coverage-" + System.nanoTime());
        return properties;
    }
}
