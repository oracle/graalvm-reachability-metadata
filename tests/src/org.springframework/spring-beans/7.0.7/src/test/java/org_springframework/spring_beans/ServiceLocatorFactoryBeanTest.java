/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServiceLocatorFactoryBeanTest {
    @Test
    void createsLocatorAndTranslatesMissingServiceExceptions() {
        for (Class<? extends RuntimeException> exceptionType : List.of(
                IllegalStateException.class, CauseOnlyException.class, MessageOnlyException.class)) {
            DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
            factory.registerSingleton("available", "service");
            ServiceLocatorFactoryBean locatorFactory = new ServiceLocatorFactoryBean();
            locatorFactory.setServiceLocatorInterface(Locator.class);
            locatorFactory.setServiceLocatorExceptionClass(exceptionType);
            locatorFactory.setBeanFactory(factory);
            locatorFactory.afterPropertiesSet();
            Locator locator = (Locator) locatorFactory.getObject();
            assertEquals("service", locator.find("available"));
            RuntimeException failure = assertThrows(exceptionType, () -> locator.find("absent"));
            assertNotNull(failure.getMessage());
        }
    }

    public interface Locator {
        String find(String id);
    }

    public static class CauseOnlyException extends RuntimeException {
        public CauseOnlyException(Throwable cause) {
            super(cause);
        }
    }

    public static class MessageOnlyException extends RuntimeException {
        public MessageOnlyException(String message) {
            super(message);
        }
    }
}
