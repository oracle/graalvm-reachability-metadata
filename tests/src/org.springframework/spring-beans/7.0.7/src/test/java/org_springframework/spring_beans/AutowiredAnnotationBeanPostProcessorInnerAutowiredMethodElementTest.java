/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertSame;

public class AutowiredAnnotationBeanPostProcessorInnerAutowiredMethodElementTest {
    @Test
    void injectsMethodArgumentsForRepeatedPrototypeInstances() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(factory);
        factory.addBeanPostProcessor(processor);
        Service service = new Service();
        factory.registerSingleton("service", service);
        RootBeanDefinition definition = new RootBeanDefinition(Client.class);
        definition.setScope("prototype");
        factory.registerBeanDefinition("client", definition);
        assertSame(service, factory.getBean("client", Client.class).service);
        assertSame(service, factory.getBean("client", Client.class).service);
    }

    public static class Service {
    }

    public static class Client {
        private Service service;

        @Autowired
        void configure(Service service) {
            this.service = service;
        }
    }
}
