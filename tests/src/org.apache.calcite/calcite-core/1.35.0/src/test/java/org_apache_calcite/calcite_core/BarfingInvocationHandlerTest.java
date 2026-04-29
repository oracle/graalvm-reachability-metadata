/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.util.BarfingInvocationHandler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

public class BarfingInvocationHandlerTest {
    @Test
    public void proxyDispatchesInterfaceCallToHandlerMethod() {
        GreetingService greetingService = createGreetingService(new GreetingHandler());

        String greeting = greetingService.greet("Calcite");

        assertThat(greeting).isEqualTo("Hello, Calcite");
    }

    private static GreetingService createGreetingService(GreetingHandler handler) {
        return GreetingService.class.cast(Proxy.newProxyInstance(
            BarfingInvocationHandlerTest.class.getClassLoader(),
            new Class<?>[] {GreetingService.class},
            handler));
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class GreetingHandler extends BarfingInvocationHandler {
        public String greet(String name) {
            return "Hello, " + name;
        }
    }
}
