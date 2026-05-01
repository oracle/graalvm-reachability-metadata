/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLogAnonymous1Test {
    private static final String SIMPLE_LOG_CLASS_CACHE = "class$org$apache$commons$logging$impl$SimpleLog";

    @Test
    void readsFromSystemResourcesWhenNoClassLoaderIsAvailable() throws Exception {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        Field simpleLogClassCache = SimpleLog.class.getDeclaredField(SIMPLE_LOG_CLASS_CACHE);
        simpleLogClassCache.setAccessible(true);
        Method getResourceAsStream = SimpleLog.class.getDeclaredMethod("getResourceAsStream", String.class);
        getResourceAsStream.setAccessible(true);
        Object previousSimpleLogClass = simpleLogClassCache.get(null);
        Thread.currentThread().setContextClassLoader(null);
        simpleLogClassCache.set(null, String.class);

        try {
            InputStream resource = (InputStream) getResourceAsStream.invoke(
                    null,
                    "org_slf4j/jcl_over_slf4j/missing-simplelog.properties"
            );

            assertThat(resource).isNull();
        } finally {
            simpleLogClassCache.set(null, previousSimpleLogClass);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }
}
