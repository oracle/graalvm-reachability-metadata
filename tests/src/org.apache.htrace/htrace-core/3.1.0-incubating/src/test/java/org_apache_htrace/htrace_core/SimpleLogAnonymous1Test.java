/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.PrivilegedAction;

import org.apache.htrace.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLogAnonymous1Test {
    @Test
    void fallsBackToSystemResourceLookupWhenNoClassLoaderIsAvailable() throws Exception {
        Field fallbackClassField = SimpleLog.class.getDeclaredField("class$org$apache$commons$logging$impl$SimpleLog");
        fallbackClassField.setAccessible(true);
        Object previousFallbackClass = fallbackClassField.get(null);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            fallbackClassField.set(null, String.class);
            Thread.currentThread().setContextClassLoader(null);
            PrivilegedAction<Object> resourceLookup = newSimpleLogResourceLookup(
                    "org_apache_htrace/htrace_core/missing-simplelog.properties");

            Object resource = resourceLookup.run();

            assertThat(resource).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            fallbackClassField.set(null, previousFallbackClass);
        }
    }

    @SuppressWarnings("unchecked")
    private static PrivilegedAction<Object> newSimpleLogResourceLookup(String resourceName) throws Exception {
        Class<?> actionClass = Class.forName("org.apache.htrace.commons.logging.impl.SimpleLog$1");
        Constructor<?> constructor = actionClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return (PrivilegedAction<Object>) constructor.newInstance(resourceName);
    }
}
