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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AutowiredAnnotationBeanPostProcessorTest {
    @Test
    void choosesAnnotatedConstructorInsteadOfDefault() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(factory);
        factory.addBeanPostProcessor(processor);
        factory.registerSingleton("name", "injected");
        factory.registerBeanDefinition("client", new RootBeanDefinition(Client.class));
        assertEquals("injected", factory.getBean("client", Client.class).name);
    }

    public static class Client {
        private final String name;

        public Client() {
            this.name = "default";
        }

        @Autowired
        public Client(String name) {
            this.name = name;
        }
    }
}
