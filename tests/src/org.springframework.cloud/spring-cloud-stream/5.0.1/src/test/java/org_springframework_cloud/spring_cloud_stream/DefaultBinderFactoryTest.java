/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderConfiguration;
import org.springframework.cloud.stream.binder.BinderType;
import org.springframework.cloud.stream.binder.BinderTypeRegistry;
import org.springframework.cloud.stream.binder.Binding;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.DefaultBinderFactory;
import org.springframework.cloud.stream.binder.ProducerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.messaging.MessageChannel;

public class DefaultBinderFactoryTest {

    @Test
    void getBinderLoadsUserSourcesAndPropagatesSharedBeans() {
        Map<String, Object> binderProperties = new HashMap<>();
        binderProperties.put("spring.main.sources", UserSourceConfiguration.class.getName());
        Map<String, BinderConfiguration> binderConfigurations = Collections.singletonMap("testBinder",
                new BinderConfiguration("testType", binderProperties, false, true));
        BinderType binderType = new BinderType("testBinder", new Class<?>[] {TestBinderConfiguration.class });
        DefaultBinderFactory binderFactory = new DefaultBinderFactory(binderConfigurations,
                new SingleBinderTypeRegistry("testType", binderType), null);

        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            applicationContext.refresh();
            binderFactory.setApplicationContext(applicationContext);
            try {
                Binder<MessageChannel, ?, ?> binder = binderFactory.getBinder("testBinder", MessageChannel.class);

                assertNotNull(binder);
                assertSame(TestMessageChannelBinder.class, binder.getClass());
            } finally {
                binderFactory.destroy();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class UserSourceConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    public static class TestBinderConfiguration {

        @Bean
        public TestMessageChannelBinder testMessageChannelBinder() {
            return new TestMessageChannelBinder();
        }
    }

    public static class TestMessageChannelBinder
            implements Binder<MessageChannel, ConsumerProperties, ProducerProperties> {

        @Override
        public Binding<MessageChannel> bindConsumer(String name, String group, MessageChannel inboundBindTarget,
                ConsumerProperties consumerProperties) {
            return null;
        }

        @Override
        public Binding<MessageChannel> bindProducer(String name, MessageChannel outboundBindTarget,
                ProducerProperties producerProperties) {
            return null;
        }
    }

    private static final class SingleBinderTypeRegistry implements BinderTypeRegistry {

        private final String binderTypeName;

        private final BinderType binderType;

        private SingleBinderTypeRegistry(String binderTypeName, BinderType binderType) {
            this.binderTypeName = binderTypeName;
            this.binderType = binderType;
        }

        @Override
        public BinderType get(String name) {
            if (this.binderTypeName.equals(name)) {
                return this.binderType;
            }
            return null;
        }

        @Override
        public Map<String, BinderType> getAll() {
            return Collections.singletonMap(this.binderTypeName, this.binderType);
        }
    }
}
