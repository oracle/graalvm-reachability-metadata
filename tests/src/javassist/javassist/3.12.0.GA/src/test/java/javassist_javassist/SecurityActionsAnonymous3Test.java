/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.PrivilegedExceptionAction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous3Test {
    @Test
    void privilegedExceptionActionRunReturnsDeclaredMethod() throws Throwable {
        Class<?> actionClass = SecurityActionsAnonymous3Test.class.getClassLoader()
                .loadClass("javassist.util.proxy.SecurityActions$3");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(
                actionClass,
                MethodType.methodType(void.class, Class.class, String.class, Class[].class));
        PrivilegedExceptionAction<?> action = (PrivilegedExceptionAction<?>) constructor.invoke(
                LookupTarget.class,
                "message",
                new Class<?>[] {String.class});

        Object method = action.run();

        assertThat(method.toString()).contains("LookupTarget.message(java.lang.String)");
    }

    public static class LookupTarget {
        public String message(String value) {
            return "hello " + value;
        }
    }
}
