/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.reflect.Reflection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Reflection.initialize(Initialized.class);
        assertTrue(initialized);
        Greeting greeting = Reflection.newProxy(Greeting.class, (proxy, method, arguments) -> "Hello " + arguments[0]);
        assertEquals("Hello Ada", greeting.hello("Ada"));
    }

    private static boolean initialized;

    public static class Initialized {
        static {
            initialized = true;
        }
    }

    public interface Greeting {
        String hello(String name);
    }
}
