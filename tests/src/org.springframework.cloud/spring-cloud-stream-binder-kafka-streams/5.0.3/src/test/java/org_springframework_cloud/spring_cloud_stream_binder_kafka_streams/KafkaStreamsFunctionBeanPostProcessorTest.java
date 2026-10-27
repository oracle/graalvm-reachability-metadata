/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_streams;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Function;

import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.cloud.stream.binder.kafka.streams.function.KafkaStreamsFunctionBeanPostProcessor;
import org.springframework.cloud.stream.function.StreamFunctionProperties;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaStreamsFunctionBeanPostProcessorTest {
    private static final String FUNCTION_NAME = "inheritedStreamProcessor";

    @Test
    void resolvesTheInheritedApplyMethodOfAComponentFunction() {
        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            applicationContext.registerBeanDefinition(FUNCTION_NAME,
                    new AnnotatedGenericBeanDefinition(InheritedStreamProcessor.class));
            applicationContext.refresh();

            KafkaStreamsFunctionBeanPostProcessor postProcessor =
                    new KafkaStreamsFunctionBeanPostProcessor(new StreamFunctionProperties());
            postProcessor.setBeanFactory(applicationContext.getBeanFactory());
            postProcessor.setApplicationContext(applicationContext);
            postProcessor.afterPropertiesSet();

            assertThat(postProcessor.getMethods()).containsOnlyKeys(FUNCTION_NAME);
            Method resolvedMethod = postProcessor.getMethods().get(FUNCTION_NAME);
            assertThat(resolvedMethod.getName()).isEqualTo("apply");
            assertThat(resolvedMethod.getDeclaringClass()).isEqualTo(AbstractStreamProcessor.class);
        }
    }

    @Component(FUNCTION_NAME)
    private static final class InheritedStreamProcessor extends AbstractStreamProcessor {
    }

    private abstract static class AbstractStreamProcessor
            implements Function<KStream<String, String>, KStream<String, String>> {
        @Override
        public KStream<String, String> apply(KStream<String, String> input) {
            return input.mapValues(new UppercaseValueMapper());
        }
    }

    private static final class UppercaseValueMapper implements ValueMapper<String, String> {
        @Override
        public String apply(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    }
}
