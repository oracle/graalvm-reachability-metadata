/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractBeanDefinitionTest {
    @Test
    @SuppressWarnings("deprecation")
    void autodetectsConstructorAndSetterAutowiring() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        Service service = new Service();
        factory.registerSingleton("service", service);
        RootBeanDefinition constructor = new RootBeanDefinition(ConstructorClient.class);
        constructor.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_AUTODETECT);
        factory.registerBeanDefinition("constructor", constructor);
        RootBeanDefinition setter = new RootBeanDefinition(SetterClient.class);
        setter.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_AUTODETECT);
        factory.registerBeanDefinition("setter", setter);
        assertSame(service, factory.getBean("constructor", ConstructorClient.class).service);
        assertSame(service, factory.getBean("setter", SetterClient.class).service);
    }

    public static class Service {
    }

    public static class ConstructorClient {
        private final Service service;

        public ConstructorClient(Service service) {
            this.service = service;
        }
    }

    public static class SetterClient {
        private Service service;

        public void setService(Service service) {
            this.service = service;
        }
    }
}
