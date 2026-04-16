/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import java.lang.reflect.Proxy;

import com.mchange.v2.reflect.ForwardingInvocationHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardingInvocationHandlerTest {
    @Test
    void invokeForwardsNoArgMethodCallsToTheInnerObject() {
        GreetingService proxy = createProxy(new GreetingServiceImpl());

        assertThat(proxy.greet()).isEqualTo("hello");
    }

    @Test
    void invokeForwardsMethodArgumentsToTheInnerObject() {
        GreetingService proxy = createProxy(new GreetingServiceImpl());

        assertThat(proxy.repeat("na", 3)).isEqualTo("nanana");
    }

    private static GreetingService createProxy(GreetingService inner) {
        return (GreetingService) Proxy.newProxyInstance(
            ForwardingInvocationHandlerTest.class.getClassLoader(),
            new Class<?>[] {GreetingService.class},
            new ForwardingInvocationHandler(inner)
        );
    }

    public interface GreetingService {
        String greet();

        String repeat(String text, int count);
    }

    public static final class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet() {
            return "hello";
        }

        @Override
        public String repeat(String text, int count) {
            return text.repeat(count);
        }
    }
}
