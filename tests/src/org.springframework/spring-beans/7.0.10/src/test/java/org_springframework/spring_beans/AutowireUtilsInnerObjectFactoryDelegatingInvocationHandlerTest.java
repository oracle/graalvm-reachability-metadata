/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.io.Serializable;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class AutowireUtilsInnerObjectFactoryDelegatingInvocationHandlerTest {
    @Test
    void delegatesInterfaceCallToObjectFactoryProduct() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerResolvableDependency(Greeting.class, new GreetingFactory());
        Field field = InjectionTarget.class.getDeclaredField("greeting");
        Greeting proxy = (Greeting) beanFactory.resolveDependency(new DependencyDescriptor(field, true), null);

        assertThat(proxy.value()).isEqualTo("delegated");
    }

    public interface Greeting {
        String value();
    }

    public static class GreetingFactory implements ObjectFactory<Greeting>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Greeting getObject() {
            return new DefaultGreeting("delegated");
        }
    }

    public static class DefaultGreeting implements Greeting {
        private final String value;

        public DefaultGreeting(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return this.value;
        }
    }

    public static class InjectionTarget {
        private Greeting greeting;
    }
}
