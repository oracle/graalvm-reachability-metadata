/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.cloud.stream.binder.kafka.KafkaExpressionEvaluatingInterceptor;
import org.springframework.cloud.stream.binder.kafka.KafkaNullConverter;
import org.springframework.cloud.stream.binder.kafka.aot.KafkaBinderRuntimeHints;
import org.springframework.cloud.stream.binder.kafka.config.ExtendedBindingHandlerMappingsProviderConfiguration;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaExtendedBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.config.BindingHandlerAdvise.MappingsProvider;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.support.KafkaNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public class Spring_cloud_stream_binder_kafkaTest {

    @Test
    void kafkaNullConverterRoundTripsTombstonePayload() {
        KafkaNullConverter converter = new KafkaNullConverter();
        Message<KafkaNull> message = MessageBuilder.withPayload(KafkaNull.INSTANCE)
                .setHeader("source", "delete-event")
                .build();

        Object convertedFromMessage = converter.fromMessage(message, KafkaNull.class);
        Message<?> convertedToMessage = converter.toMessage(KafkaNull.INSTANCE, message.getHeaders());

        assertThat(convertedFromMessage).isSameAs(KafkaNull.INSTANCE);
        assertThat(convertedToMessage.getPayload()).isSameAs(KafkaNull.INSTANCE);
        assertThat(convertedToMessage.getHeaders()).containsEntry("source", "delete-event");
    }

    @Test
    void expressionInterceptorAddsEvaluatedKafkaMessageKeyHeader() {
        KafkaExpressionEvaluatingInterceptor interceptor = new KafkaExpressionEvaluatingInterceptor(
                new SpelExpressionParser().parseExpression("payload['tenant'] + ':' + headers['eventType']"),
                new StandardEvaluationContext());
        Message<Map<String, String>> message = MessageBuilder.withPayload(Map.of("tenant", "acme"))
                .setHeader("eventType", "order.created")
                .build();

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result.getPayload()).isSameAs(message.getPayload());
        assertThat(result.getHeaders())
                .containsEntry(KafkaExpressionEvaluatingInterceptor.MESSAGE_KEY_HEADER, "acme:order.created");
        assertThat(message.getHeaders()).doesNotContainKey(KafkaExpressionEvaluatingInterceptor.MESSAGE_KEY_HEADER);
    }

    @Test
    void configurationObjectsExposeKafkaExtendedBindingMappingsAndRuntimeHints() {
        MappingsProvider mappingsProvider = new ExtendedBindingHandlerMappingsProviderConfiguration()
                .kafkaExtendedPropertiesDefaultMappingsProvider();
        RuntimeHints hints = new RuntimeHints();

        new KafkaBinderRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(mappingsProvider.getDefaultMappings()).containsEntry(
                ConfigurationPropertyName.of("spring.cloud.stream.kafka.bindings"),
                ConfigurationPropertyName.of("spring.cloud.stream.kafka.default"));
        assertRegisteredBindingPropertyHint(hints, KafkaConsumerProperties.class);
        assertRegisteredBindingPropertyHint(hints, KafkaProducerProperties.class);
        assertRegisteredBindingPropertyHint(hints, KafkaExtendedBindingProperties.class);
        assertRegisteredBindingPropertyHint(hints, KafkaBindingProperties.class);
    }

    private static void assertRegisteredBindingPropertyHint(RuntimeHints hints, Class<?> type) {
        TypeHint typeHint = hints.reflection().getTypeHint(type);
        assertThat(typeHint).as("runtime hint for %s", type.getName()).isNotNull();
        assertThat(typeHint.getMemberCategories()).contains(MemberCategory.INVOKE_DECLARED_METHODS);
    }
}
