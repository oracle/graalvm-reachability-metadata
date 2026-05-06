/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class AutowireUtilsTest {

    @Test
    public void autowiringSerializableObjectFactoryForInterfaceCreatesLazyProxy() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        SerializableGreetingServiceFactory serviceFactory = new SerializableGreetingServiceFactory();
        beanFactory.registerResolvableDependency(GreetingService.class, serviceFactory);

        RootBeanDefinition beanDefinition = new RootBeanDefinition(ClientBean.class);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        beanFactory.registerBeanDefinition("clientBean", beanDefinition);

        ClientBean clientBean = beanFactory.getBean("clientBean", ClientBean.class);

        assertThat(clientBean.hasGreetingService()).isTrue();
        assertThat(serviceFactory.getObjectCallCount()).isZero();
    }

    public interface GreetingService {
        String greeting();
    }

    public static class ClientBean {
        private GreetingService greetingService;

        public void setGreetingService(GreetingService greetingService) {
            this.greetingService = greetingService;
        }

        public boolean hasGreetingService() {
            return this.greetingService != null;
        }
    }

    private static final class SerializableGreetingServiceFactory implements ObjectFactory<GreetingService>,
            Serializable {
        private static final long serialVersionUID = 1L;

        private int objectCallCount;

        @Override
        public GreetingService getObject() {
            this.objectCallCount++;
            return () -> "hello";
        }

        private int getObjectCallCount() {
            return this.objectCallCount;
        }
    }
}
