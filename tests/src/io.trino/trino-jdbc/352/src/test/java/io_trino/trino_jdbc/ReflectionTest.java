/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.reflect.Reflection;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionTest {
    @Test
    void initializesClassAndCreatesInterfaceProxy() throws Exception {
        Reflection.initialize(Registration.class);
        assertTrue(Registry.registered);
        Callable<?> greeting = Reflection.newProxy(Callable.class,
                (proxy, method, arguments) -> "Hello Ada");
        assertEquals("Hello Ada", greeting.call());
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
