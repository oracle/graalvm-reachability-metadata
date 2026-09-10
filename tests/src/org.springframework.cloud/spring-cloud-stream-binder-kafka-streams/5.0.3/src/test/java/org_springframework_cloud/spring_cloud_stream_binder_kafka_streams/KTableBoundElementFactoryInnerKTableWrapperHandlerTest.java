/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_streams;

import java.util.Map;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.streams.EncodingDecodingBindAdviceHandler;
import org.springframework.cloud.stream.binder.kafka.streams.KTableBoundElementFactory;
import org.springframework.cloud.stream.binder.kafka.streams.KafkaStreamsBinderSupportAutoConfiguration;
import org.springframework.cloud.stream.binder.kafka.streams.KafkaStreamsBindingInformationCatalogue;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;

import static org.assertj.core.api.Assertions.assertThat;

public class KTableBoundElementFactoryInnerKTableWrapperHandlerTest {
    private static final String BINDING_NAME = "orderStatus";
    private static final String INPUT_TOPIC = "order-status-input";
    private static final String OUTPUT_TOPIC = "order-status-output";
    private static final String STORE_NAME = "order-status-store";

    @Test
    void wrappedKTableExposesItsStoreAndForwardsUpdates() {
        StreamsBuilder streamsBuilder = new StreamsBuilder();
        KTable<String, String> delegate = streamsBuilder.table(INPUT_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(STORE_NAME)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.String()));
        KTable<String, String> bindingTarget = asStringTable(factory().createInput(BINDING_NAME));

        KTableBoundElementFactory.KTableWrapper wrapper =
                (KTableBoundElementFactory.KTableWrapper) bindingTarget;
        wrapper.wrap(asObjectTable(delegate));

        assertThat(bindingTarget.queryableStoreName()).isEqualTo(STORE_NAME);
        bindingTarget.toStream(Named.as("order-status-stream"))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        try (TopologyTestDriver driver = new TopologyTestDriver(streamsBuilder.build())) {
            TestInputTopic<String, String> input = driver.createInputTopic(INPUT_TOPIC,
                    new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> output = driver.createOutputTopic(OUTPUT_TOPIC,
                    new StringDeserializer(), new StringDeserializer());

            input.pipeInput("order-42", "shipped");

            KeyValue<String, String> result = output.readKeyValue();
            assertThat(result.key).isEqualTo("order-42");
            assertThat(result.value).isEqualTo("shipped");
        }
    }

    private static KTableBoundElementFactory factory() {
        KafkaStreamsBinderSupportAutoConfiguration configuration =
                new KafkaStreamsBinderSupportAutoConfiguration();
        KafkaStreamsBindingInformationCatalogue catalogue = configuration
                .kafkaStreamsBindingInformationCatalogue();
        return configuration.kTableBoundElementFactory(bindingServiceProperties(),
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
    private static KTable<String, String> asStringTable(KTable<?, ?> table) {
        return (KTable<String, String>) table;
    }

    @SuppressWarnings("unchecked")
    private static KTable<Object, Object> asObjectTable(KTable<?, ?> table) {
        return (KTable<Object, Object>) table;
    }
}
