/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_streams;

import java.util.Locale;
import java.util.Map;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.streams.EncodingDecodingBindAdviceHandler;
import org.springframework.cloud.stream.binder.kafka.streams.KStreamBoundElementFactory;
import org.springframework.cloud.stream.binder.kafka.streams.KafkaStreamsBinderSupportAutoConfiguration;
import org.springframework.cloud.stream.binder.kafka.streams.KafkaStreamsBindingInformationCatalogue;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;

import static org.assertj.core.api.Assertions.assertThat;

public class KStreamBoundElementFactoryInnerKStreamWrapperHandlerTest {
    private static final String BINDING_NAME = "orders";
    private static final String INPUT_TOPIC = "orders-input";
    private static final String OUTPUT_TOPIC = "orders-output";

    @Test
    void wrappedKStreamTransformsRecords() {
        StreamsBuilder streamsBuilder = new StreamsBuilder();
        KStream<String, String> delegate = streamsBuilder.stream(INPUT_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> bindingTarget = asStringStream(factory().createInput(BINDING_NAME));

        KStreamBoundElementFactory.KStreamWrapper wrapper =
                (KStreamBoundElementFactory.KStreamWrapper) bindingTarget;
        wrapper.wrap(asObjectStream(delegate));
        bindingTarget.mapValues(new UppercaseValueMapper(), Named.as("uppercase-values"))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        try (TopologyTestDriver driver = new TopologyTestDriver(streamsBuilder.build())) {
            TestInputTopic<String, String> input = driver.createInputTopic(INPUT_TOPIC,
                    new StringSerializer(), new StringSerializer());
            TestOutputTopic<String, String> output = driver.createOutputTopic(OUTPUT_TOPIC,
                    new StringDeserializer(), new StringDeserializer());

            input.pipeInput("order-42", "ready");

            KeyValue<String, String> result = output.readKeyValue();
            assertThat(result.key).isEqualTo("order-42");
            assertThat(result.value).isEqualTo("READY");
        }
    }

    private static KStreamBoundElementFactory factory() {
        KafkaStreamsBinderSupportAutoConfiguration configuration =
                new KafkaStreamsBinderSupportAutoConfiguration();
        KafkaStreamsBindingInformationCatalogue catalogue = configuration
                .kafkaStreamsBindingInformationCatalogue();
        return configuration.kStreamBoundElementFactory(bindingServiceProperties(), catalogue,
                new EncodingDecodingBindAdviceHandler());
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
    private static KStream<String, String> asStringStream(KStream<?, ?> stream) {
        return (KStream<String, String>) stream;
    }

    @SuppressWarnings("unchecked")
    private static KStream<Object, Object> asObjectStream(KStream<?, ?> stream) {
        return (KStream<Object, Object>) stream;
    }

    private static final class UppercaseValueMapper implements ValueMapper<String, String> {
        @Override
        public String apply(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    }
}
