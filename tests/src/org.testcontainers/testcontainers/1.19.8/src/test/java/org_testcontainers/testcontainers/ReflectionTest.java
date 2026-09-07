/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.reflect.Reflection;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionTest {
    @Test
    void createsInterfaceProxies() {
        Reflection.initialize(Greeting.class);
        Greeting greeting = Reflection.newProxy(
            Greeting.class,
            (proxy, method, arguments) -> "hello " + arguments[0]
        );

        assertThat(greeting.greet("native")).isEqualTo("hello native");
    }

    public interface Greeting {
        String greet(String name);
    }
}
