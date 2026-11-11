/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AbstractFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractFactoryBeanTest {
    @Test
    void exposesEarlySingletonReferenceBeforeInitialization() throws Exception {
        GreetingFactory factory = new GreetingFactory();
        Greeting early = factory.getObject();
        assertSame(early, factory.getObject());
        factory.afterPropertiesSet();
        assertEquals("hello", early.message());
        assertEquals("hello", factory.getObject().message());
        factory.destroy();
    }

    public interface Greeting {
        String message();
    }

    public static class GreetingFactory extends AbstractFactoryBean<Greeting> {
        @Override
        public Class<?> getObjectType() {
            return Greeting.class;
        }

        @Override
        protected Greeting createInstance() {
            return () -> "hello";
        }
    }
}
