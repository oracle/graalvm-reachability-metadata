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

public class SecurityActionsAnonymous2Test {
    @Test
    void privilegedActionRunReturnsDeclaredConstructors() throws Throwable {
        Class<?> actionClass = SecurityActionsAnonymous2Test.class.getClassLoader()
                .loadClass("javassist.util.proxy.SecurityActions$2");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(actionClass, MethodType.methodType(void.class, Class.class));
        PrivilegedAction<?> action = (PrivilegedAction<?>) constructor.invoke(ConstructorTarget.class);

        Object[] constructors = (Object[]) action.run();

        assertThat(constructors)
                .extracting(Object::toString)
                .anyMatch(signature -> signature.contains("ConstructorTarget(java.lang.String)"));
    }

    public static class ConstructorTarget {
        private final String value;

        public ConstructorTarget() {
            this("default");
        }

        public ConstructorTarget(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
