/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AbstractFactoryBean;

public class AbstractFactoryBeanTest {

    @Test
    public void getObjectBeforeInitializationCreatesEarlySingletonProxy() throws Exception {
        GreetingServiceFactoryBean factoryBean = new GreetingServiceFactoryBean();

        GreetingService earlySingleton = factoryBean.getObject();

        assertThat(earlySingleton).isNotSameAs(factoryBean.instance);
        assertThat(earlySingleton.toString()).contains(GreetingService.class.getName());
        factoryBean.afterPropertiesSet();
        assertThat(earlySingleton.greet("Spring")).isEqualTo("Hello, Spring");
        assertThat(factoryBean.getObject()).isSameAs(factoryBean.instance);
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class GreetingServiceFactoryBean extends AbstractFactoryBean<GreetingService> {
        private GreetingService instance;

        @Override
        public Class<?> getObjectType() {
            return GreetingService.class;
        }

        @Override
        protected GreetingService createInstance() {
            this.instance = new DefaultGreetingService();
            return this.instance;
        }
    }

    public static class DefaultGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }
}
