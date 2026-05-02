/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.reflect.ForwardingInvocationHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardingInvocationHandlerTest {
    @Test
    void proxyInvocationHandlerForwardsCallsToInnerImplementation() {
        GreetingService proxy = (GreetingService) Proxy.newProxyInstance(
                GreetingService.class.getClassLoader(),
                new Class<?>[]{GreetingService.class},
                new ForwardingInvocationHandler(new GreetingServiceImpl("hello "))
        );

        assertThat(proxy.greet("metadata")).isEqualTo("hello metadata");
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static final class GreetingServiceImpl implements GreetingService {
        private final String prefix;

        public GreetingServiceImpl(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String greet(String name) {
            return prefix + name;
        }
    }
}
