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
    void delegatesInterfaceMethodToHandlerMethod() {
        Greeting greeting = (Greeting) Proxy.newProxyInstance(
                Greeting.class.getClassLoader(),
                new Class<?>[] {Greeting.class},
                new GreetingHandler());

        assertThat(greeting.hello("Ada")).isEqualTo("Hello Ada");
    }

    public interface Greeting {
        String hello(String name);
    }

    public static class GreetingHandler extends BarfingInvocationHandler {
        public String hello(String name) {
            return "Hello " + name;
        }
    }
}
