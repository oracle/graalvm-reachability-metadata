/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.reflect.Reflection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionTest {
    @Test
    void initializesClassAndCreatesInterfaceProxy() {
        Reflection.initialize(Registration.class);
        assertTrue(Registry.registered);
        Greeting greeting = Reflection.newProxy(Greeting.class, (proxy, method, arguments) -> "Hello " + arguments[0]);
        assertEquals("Hello Ada", greeting.greet("Ada"));
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class Registry {
        static boolean registered;
    }

    public static class Registration {
        static {
            Registry.registered = true;
        }
    }
}
