/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.ProxyFactory;
import com.thoughtworks.proxy.factory.StandardProxyFactory;
import com.thoughtworks.proxy.kit.SimpleInvoker;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class SimpleInvokerTest {
    @Test
    void invokesTargetMethodThroughProxyCall() {
        GreetingService target = new GreetingTarget("Ada");
        ProxyFactory proxyFactory = new StandardProxyFactory();
        GreetingService proxy = proxyFactory.createProxy(new SimpleInvoker(target), GreetingService.class);

        String greeting = proxy.greeting("Lovelace");

        assertThat(greeting).isEqualTo("Hello Ada Lovelace");
    }

    public interface GreetingService extends Serializable {
        String greeting(String lastName);
    }

    public static final class GreetingTarget implements GreetingService {
        private static final long serialVersionUID = 1L;

        private final String firstName;

        public GreetingTarget(String firstName) {
            this.firstName = firstName;
        }

        @Override
        public String greeting(String lastName) {
            return "Hello " + firstName + " " + lastName;
        }
    }
}
