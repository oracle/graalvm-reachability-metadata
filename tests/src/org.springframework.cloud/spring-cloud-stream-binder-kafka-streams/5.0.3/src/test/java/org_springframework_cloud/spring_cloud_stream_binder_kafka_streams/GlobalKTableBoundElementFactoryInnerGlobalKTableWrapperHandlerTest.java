/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_streams;

import java.util.Map;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyDescription;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.streams.EncodingDecodingBindAdviceHandler;
import org.springframework.cloud.stream.binder.kafka.streams.GlobalKTableBoundElementFactory;
import org.springframework.cloud.stream.binder.kafka.streams.KafkaStreamsBinderSupportAutoConfiguration;
import org.springframework.cloud.stream.binder.kafka.streams.KafkaStreamsBindingInformationCatalogue;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalKTableBoundElementFactoryInnerGlobalKTableWrapperHandlerTest {
    private static final String BINDING_NAME = "productCatalog";
    private static final String INPUT_TOPIC = "product-catalog-input";
    private static final String STORE_NAME = "product-catalog-store";

    @Test
    void wrappedGlobalKTableExposesMaterializedRecords() {
        StreamsBuilder streamsBuilder = new StreamsBuilder();
        GlobalKTable<String, String> delegate = streamsBuilder.globalTable(INPUT_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(STORE_NAME)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.String()));
        GlobalKTable<String, String> bindingTarget = asStringTable(factory().createInput(BINDING_NAME));

        GlobalKTableBoundElementFactory.GlobalKTableWrapper wrapper =
                (GlobalKTableBoundElementFactory.GlobalKTableWrapper) bindingTarget;
        wrapper.wrap(asObjectTable(delegate));

        assertThat(bindingTarget.queryableStoreName()).isEqualTo(STORE_NAME);
        Topology topology = streamsBuilder.build();
        TopologyDescription topologyDescription = topology.describe();
        assertThat(topologyDescription.globalStores()).hasSize(1);
        TopologyDescription.GlobalStore globalStore = topologyDescription.globalStores().iterator().next();
        assertThat(globalStore.source().topicSet()).containsExactly(INPUT_TOPIC);
        assertThat(globalStore.processor().stores()).contains(STORE_NAME);

        try (TopologyTestDriver driver = new TopologyTestDriver(topology)) {
            TestInputTopic<String, String> input = driver.createInputTopic(INPUT_TOPIC,
                    new StringSerializer(), new StringSerializer());
            input.pipeInput("product-42", "available");

            assertThat(driver.<String, String>getKeyValueStore(STORE_NAME).get("product-42"))
                    .isEqualTo("available");
        }
    }

    private static GlobalKTableBoundElementFactory factory() {
        KafkaStreamsBinderSupportAutoConfiguration configuration =
                new KafkaStreamsBinderSupportAutoConfiguration();
        KafkaStreamsBindingInformationCatalogue catalogue = configuration
                .kafkaStreamsBindingInformationCatalogue();
        return configuration.globalKTableBoundElementFactory(bindingServiceProperties(),
                new EncodingDecodingBindAdviceHandler(), catalogue);
    }

    private static BindingServiceProperties bindingServiceProperties() {
        ConsumerProperties consumer = new ConsumerProperties();
        BindingProperties binding = new BindingProperties();
        binding.setConsumer(consumer);
        BindingServiceProperties properties = new BindingServiceProperties();
        properties.setBindings(Map.of(BINDING_NAME, binding));
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static GlobalKTable<String, String> asStringTable(GlobalKTable<?, ?> table) {
        return (GlobalKTable<String, String>) table;
    }

    @SuppressWarnings("unchecked")
    private static GlobalKTable<Object, Object> asObjectTable(GlobalKTable<?, ?> table) {
        return (GlobalKTable<Object, Object>) table;
    }
}
