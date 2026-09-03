/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AbstractFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractFactoryBeanInnerEarlySingletonInvocationHandlerTest {
    @Test
    void delegatesEarlyProxyAfterFactoryInitialization() throws Exception {
        GreetingFactory factory = new GreetingFactory();
        Greeting early = factory.getObject();

        factory.afterPropertiesSet();

        assertThat(early.value()).isEqualTo("initialized");
    }

    public interface Greeting {
        String value();
    }

    public static class GreetingFactory extends AbstractFactoryBean<Greeting> {
        @Override
        public Class<?> getObjectType() {
            return Greeting.class;
        }

        @Override
        protected Greeting createInstance() {
            return new DefaultGreeting();
        }
    }

    public static class DefaultGreeting implements Greeting {
        @Override
        public String value() {
            return "initialized";
        }
    }
}
