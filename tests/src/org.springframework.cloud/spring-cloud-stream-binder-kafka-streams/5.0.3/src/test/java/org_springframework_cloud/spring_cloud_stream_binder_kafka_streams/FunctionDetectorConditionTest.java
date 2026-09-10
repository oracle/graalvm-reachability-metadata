/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_streams;

import java.util.Locale;
import java.util.function.Function;

import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.cloud.stream.binder.kafka.streams.function.FunctionDetectorCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionDetectorConditionTest {
    private static final String FUNCTION_NAME = "inheritedStreamProcessor";

    @Test
    void matchesAnInheritedKafkaStreamsComponentFunction() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        AnnotatedGenericBeanDefinition functionDefinition =
                new AnnotatedGenericBeanDefinition(InheritedStreamProcessor.class);
        beanFactory.registerBeanDefinition(FUNCTION_NAME, functionDefinition);

        FunctionDetectorCondition condition = new FunctionDetectorCondition();
        ConditionOutcome outcome = condition.getMatchOutcome(
                new BeanFactoryConditionContext(beanFactory), functionDefinition.getMetadata());

        assertThat(outcome.isMatch()).isTrue();
        assertThat(outcome.getMessage()).contains("Function/BiFunction/Consumer beans found");
    }

    private static final class BeanFactoryConditionContext implements ConditionContext {
        private final DefaultListableBeanFactory beanFactory;
        private final Environment environment = new StandardEnvironment();
        private final ResourceLoader resourceLoader = new DefaultResourceLoader();

        private BeanFactoryConditionContext(DefaultListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public BeanDefinitionRegistry getRegistry() {
            return this.beanFactory;
        }

        @Override
        public ConfigurableListableBeanFactory getBeanFactory() {
            return this.beanFactory;
        }

        @Override
        public Environment getEnvironment() {
            return this.environment;
        }

        @Override
        public ResourceLoader getResourceLoader() {
            return this.resourceLoader;
        }

        @Override
        public ClassLoader getClassLoader() {
            return FunctionDetectorConditionTest.class.getClassLoader();
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
