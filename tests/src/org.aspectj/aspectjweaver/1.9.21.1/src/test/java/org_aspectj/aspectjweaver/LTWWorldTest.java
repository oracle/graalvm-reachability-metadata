/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.weaver.ltw.LTWWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LTWWorldTest {
    @Test
    void discoversJdkConcurrentMapImplementationForBootstrapTypeCache() throws Exception {
        Class<?> mapClass = invokeMakeConcurrentMapClass();

        assertThat(mapClass).isEqualTo(ConcurrentHashMap.class);
        assertThat(Map.class).isAssignableFrom(mapClass);
    }

    private static Class<?> invokeMakeConcurrentMapClass() throws Exception {
        Method method = LTWWorld.class.getDeclaredMethod("makeConcurrentMapClass");
        method.setAccessible(true);
        try {
            return (Class<?>) method.invoke(null);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw exception;
        }
    }
}
