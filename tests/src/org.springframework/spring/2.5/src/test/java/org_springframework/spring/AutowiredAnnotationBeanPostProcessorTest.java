/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.SimpleInstantiationStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AutowiredAnnotationBeanPostProcessorTest {
    public static class Consumer {
        private final TestBeans.Greeting greeting;

        @Autowired
        public Consumer(TestBeans.Greeting greeting) {
            this.greeting = greeting;
        }
    }

    @Test
    void selectsAnnotatedConstructorAndInjectsDependency() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setInstantiationStrategy(new SimpleInstantiationStrategy());
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(factory);
        factory.addBeanPostProcessor(processor);
        factory.registerSingleton("greeting", new TestBeans.GreetingService());
        factory.registerBeanDefinition("consumer", new RootBeanDefinition(Consumer.class));
        try {
            Consumer consumer = (Consumer) factory.getBean("consumer");
            assertEquals("Hello injected", consumer.greeting.greet("injected"));
        } finally {
            factory.destroySingletons();
        }
    }
}
