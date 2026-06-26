/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_kafka_clients_2_6;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

public class KafkaTelemetryTest {
    @Test
    void wrapsProducerWithDynamicProxyAndDelegatesNonSendMethods() {
        KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(OpenTelemetry.noop());
        MockProducer<String, String> mockProducer = new MockProducer<>(
                true,
                new StringSerializer(),
                new StringSerializer());

        Producer<String, String> wrappedProducer = kafkaTelemetry.wrap(mockProducer);

        assertThat(wrappedProducer).isNotSameAs(mockProducer);
        Map<MetricName, ? extends Metric> metrics = wrappedProducer.metrics();

        assertThat(metrics).isEmpty();
    }

    @Test
    void wrapsConsumerWithDynamicProxyAndDelegatesNonPollMethods() {
        KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(OpenTelemetry.noop());
        MockConsumer<String, String> mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        mockConsumer.subscribe(Collections.singleton("telemetry-topic"));

        Consumer<String, String> wrappedConsumer = kafkaTelemetry.wrap(mockConsumer);

        assertThat(wrappedConsumer).isNotSameAs(mockConsumer);
        Set<String> subscription = wrappedConsumer.subscription();

        assertThat(subscription).containsExactly("telemetry-topic");
    }

    @Test
    void exposesInterceptorAndMetricsConfigurationProperties() {
        KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(OpenTelemetry.noop());

        Map<String, ?> producerInterceptorConfig =
                kafkaTelemetry.producerInterceptorConfigProperties();
        Map<String, ?> consumerInterceptorConfig =
                kafkaTelemetry.consumerInterceptorConfigProperties();
        Map<String, ?> metricConfig = kafkaTelemetry.metricConfigProperties();

        assertThat(producerInterceptorConfig)
                .containsKeys(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
        assertThat(consumerInterceptorConfig)
                .containsKeys(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG);
        assertThat(metricConfig)
                .containsKeys(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG);
    }
}
