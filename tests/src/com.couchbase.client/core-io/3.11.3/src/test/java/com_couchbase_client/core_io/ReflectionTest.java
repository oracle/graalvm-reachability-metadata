/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.base.Supplier;
import com.couchbase.client.core.deps.com.google.common.reflect.Reflection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Reflection.initialize(Initialized.class);
        assertTrue(initialized);
        Supplier<?> greeting = Reflection.newProxy(Supplier.class, (proxy, method, arguments) -> "Hello Ada");
        assertEquals("Hello Ada", greeting.get());
    }

    private static boolean initialized;

    public static class Initialized {
        static {
            initialized = true;
        }
    }

}
