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

public class AutowireUtilsTest {
    @Test
    void createsInterfaceProxyForObjectFactory() {
        ObjectFactory<Greeting> factory = new GreetingFactory();

        Object value = AutowireUtils.resolveAutowiringValue(factory, Greeting.class);

        assertThat(value).isInstanceOf(Greeting.class);
    }

    public interface Greeting {
        String value();
    }

    public static class GreetingFactory implements ObjectFactory<Greeting>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Greeting getObject() {
            return new DefaultGreeting();
        }
    }

    public static class DefaultGreeting implements Greeting {
        @Override
        public String value() {
            return "hello";
        }
    }
}
