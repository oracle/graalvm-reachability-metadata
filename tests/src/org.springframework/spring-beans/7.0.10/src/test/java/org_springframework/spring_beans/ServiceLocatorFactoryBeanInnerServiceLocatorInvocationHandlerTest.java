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

public class ServiceLocatorFactoryBeanInnerServiceLocatorInvocationHandlerTest {
    @Test
    void resolvesServiceByLocatorMethodSignature() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GreetingService service = new GreetingService("located");
        beanFactory.registerSingleton("primary", service);
        ServiceLocatorFactoryBean factory = new ServiceLocatorFactoryBean();
        factory.setServiceLocatorInterface(GreetingLocator.class);
        factory.setBeanFactory(beanFactory);
        factory.afterPropertiesSet();
        GreetingLocator locator = (GreetingLocator) factory.getObject();

        assertThat(locator.get("primary")).isSameAs(service);
        assertThat(locator.get("primary").getGreeting()).isEqualTo("located");
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
}
