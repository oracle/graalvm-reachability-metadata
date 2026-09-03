/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.support.AutowireUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class AutowireUtilsInnerObjectFactoryDelegatingInvocationHandlerTest {
    @Test
    void delegatesInterfaceCallToObjectFactoryProduct() {
        ObjectFactory<Greeting> factory = new GreetingFactory();
        Greeting proxy = (Greeting) AutowireUtils.resolveAutowiringValue(factory, Greeting.class);

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
}
