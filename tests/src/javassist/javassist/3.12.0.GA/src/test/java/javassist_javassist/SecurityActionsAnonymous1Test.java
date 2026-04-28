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
import java.security.PrivilegedAction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous1Test {
    @Test
    void privilegedActionRunReturnsDeclaredMethods() throws Throwable {
        Class<?> actionClass = SecurityActionsAnonymous1Test.class.getClassLoader()
                .loadClass("javassist.util.proxy.SecurityActions$1");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(actionClass, MethodType.methodType(void.class, Class.class));
        PrivilegedAction<?> action = (PrivilegedAction<?>) constructor.invoke(LookupTarget.class);

        Object[] methods = (Object[]) action.run();

        assertThat(methods).hasSize(1);
        assertThat(methods[0].toString()).contains("message");
    }

    public static class LookupTarget {
        public String message() {
            return "hello";
        }
    }
}
