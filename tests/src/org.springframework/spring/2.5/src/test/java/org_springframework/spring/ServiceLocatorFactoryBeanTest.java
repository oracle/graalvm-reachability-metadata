/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceLocatorFactoryBeanTest {
    public interface Services {
        TestBeans.Greeting find(String name);
    }

    public static class MessageFailure extends RuntimeException {
        public MessageFailure(String message) {
            super(message);
        }
    }

    public static class CauseFailure extends RuntimeException {
        public CauseFailure(Throwable cause) {
            super(cause);
        }
    }

    public static class FullFailure extends RuntimeException {
        public FullFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Test
    void locatesMappedServicesAndUsesCustomLookupExceptions() {
        for (Class<?> exception : new Class<?>[]{MessageFailure.class, CauseFailure.class, FullFailure.class}) {
            StaticListableBeanFactory beans = new StaticListableBeanFactory();
            TestBeans.Greeting service = new TestBeans.GreetingService();
            beans.addBean("greeting", service);
            ServiceLocatorFactoryBean factory = new ServiceLocatorFactoryBean();
            factory.setBeanFactory(beans);
            factory.setServiceLocatorInterface(Services.class);
            factory.setServiceLocatorExceptionClass(exception);
            Properties mappings = new Properties();
            mappings.setProperty("friendly", "greeting");
            factory.setServiceMappings(mappings);
            factory.afterPropertiesSet();
            Services locator = (Services) factory.getObject();
            assertSame(service, locator.find("friendly"));
            RuntimeException failure = assertThrows(RuntimeException.class, () -> locator.find("absent"));
            assertSame(exception, failure.getClass());
            assertTrue(failure.getMessage().contains("absent"));
        }
    }
}
