/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceLocatorFactoryBeanTest {
    @Test
    void createsServiceLocatorAndSelectsSupportedExceptionConstructors() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("service", new GreetingService("hello"));

        ServiceLocatorFactoryBean factory = new ServiceLocatorFactoryBean();
        factory.setServiceLocatorInterface(GreetingLocator.class);
        factory.setBeanFactory(beanFactory);
        factory.setServiceLocatorExceptionClass(MessageAndCauseException.class);
        factory.setServiceLocatorExceptionClass(CauseException.class);
        factory.setServiceLocatorExceptionClass(MessageException.class);
        factory.afterPropertiesSet();

        assertThat(factory.getObject()).isInstanceOf(GreetingLocator.class);
    }

    public interface GreetingLocator {
        GreetingService get(String name);
    }

    public static class GreetingService {
        private final String greeting;

        public GreetingService(String greeting) {
            this.greeting = greeting;
        }

        public String getGreeting() {
            return this.greeting;
        }
    }

    public static class MessageAndCauseException extends Exception {
        private static final long serialVersionUID = 1L;

        public MessageAndCauseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class CauseException extends Exception {
        private static final long serialVersionUID = 1L;

        public CauseException(Throwable cause) {
            super(cause);
        }
    }

    public static class MessageException extends Exception {
        private static final long serialVersionUID = 1L;

        public MessageException(String message) {
            super(message);
        }
    }
}
