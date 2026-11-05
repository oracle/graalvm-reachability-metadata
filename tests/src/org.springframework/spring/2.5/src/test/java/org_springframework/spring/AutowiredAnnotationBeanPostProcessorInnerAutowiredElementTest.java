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
import static org.junit.jupiter.api.Assertions.assertSame;

public class AutowiredAnnotationBeanPostProcessorInnerAutowiredElementTest {
    public static class Consumer {
        @Autowired
        private TestBeans.Greeting field;
        private TestBeans.Greeting method;

        @Autowired
        public void configure(TestBeans.Greeting service) {
            method = service;
        }
    }

    @Test
    void injectsAnnotatedFieldsAndMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setInstantiationStrategy(new SimpleInstantiationStrategy());
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(factory);
        factory.addBeanPostProcessor(processor);
        TestBeans.Greeting service = new TestBeans.GreetingService();
        factory.registerSingleton("greeting", service);
        RootBeanDefinition definition = new RootBeanDefinition(Consumer.class);
        definition.setScope("prototype");
        factory.registerBeanDefinition("consumer", definition);
        try {
            for (int index = 0; index < 2; index++) {
                Consumer consumer = (Consumer) factory.getBean("consumer");
                assertSame(service, consumer.field);
                assertSame(service, consumer.method);
                assertEquals("Hello wired", consumer.field.greet("wired"));
            }
        } finally {
            factory.destroySingletons();
        }
    }
}
