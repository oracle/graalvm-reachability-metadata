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

public class SecurityActionsAnonymous4Test {
    @Test
    void privilegedExceptionActionRunReturnsDeclaredConstructor() throws Throwable {
        Class<?> actionClass = SecurityActionsAnonymous4Test.class.getClassLoader()
                .loadClass("javassist.util.proxy.SecurityActions$4");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(
                actionClass,
                MethodType.methodType(void.class, Class.class, Class[].class));
        PrivilegedExceptionAction<?> action = (PrivilegedExceptionAction<?>) constructor.invoke(
                ConstructorTarget.class,
                new Class<?>[] {String.class, int.class});

        Object declaredConstructor = action.run();

        assertThat(declaredConstructor.toString()).contains("ConstructorTarget(java.lang.String,int)");
    }

    public static class ConstructorTarget {
        private final String label;
        private final int count;

        public ConstructorTarget(String label, int count) {
            this.label = label;
            this.count = count;
        }

        public String label() {
            return label;
        }

        public int count() {
            return count;
        }
    }
}
