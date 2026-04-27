/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    private static final String SECURITY_ACTIONS_CLASS = "javassist.util.proxy.SecurityActions";

    @Test
    void findsDeclaredConstructorThroughSecurityActions() throws Throwable {
        Class<?> securityActions = Class.forName(SECURITY_ACTIONS_CLASS);
        MethodHandle getDeclaredConstructor = securityActionsLookup(securityActions).findStatic(
                securityActions,
                "getDeclaredConstructor",
                MethodType.methodType(Constructor.class, Class.class, Class[].class));

        Constructor<?> constructor = (Constructor<?>) getDeclaredConstructor.invoke(
                ConstructorTarget.class,
                new Class<?>[] {String.class});

        assertThat(constructor.getDeclaringClass()).isEqualTo(ConstructorTarget.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void writesFieldThroughSecurityActions() throws Throwable {
        Class<?> securityActions = Class.forName(SECURITY_ACTIONS_CLASS);
        MethodHandle set = securityActionsLookup(securityActions).findStatic(
                securityActions,
                "set",
                MethodType.methodType(void.class, Field.class, Object.class, Object.class));
        MutableTarget target = new MutableTarget();
        Field field = MutableTarget.class.getField("value");

        set.invoke(field, target, "updated");

        assertThat(target.value).isEqualTo("updated");
    }

    private static MethodHandles.Lookup securityActionsLookup(Class<?> securityActions) throws IllegalAccessException {
        return MethodHandles.privateLookupIn(securityActions, MethodHandles.lookup());
    }

    public static class ConstructorTarget {
        private final String value;

        public ConstructorTarget(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class MutableTarget {
        public String value = "original";
    }
}
