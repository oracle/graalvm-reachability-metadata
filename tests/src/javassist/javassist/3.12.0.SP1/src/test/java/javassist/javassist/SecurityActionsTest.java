/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    @Test
    void getsDeclaredConstructorForSuppliedParameterTypes() throws Throwable {
        Class<?> securityActions = securityActionsType();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(securityActions, MethodHandles.lookup());
        MethodHandle getDeclaredConstructor = lookup.findStatic(securityActions, "getDeclaredConstructor",
            MethodType.methodType(Constructor.class, Class.class, Class[].class));

        Constructor<?> constructor = (Constructor<?>) getDeclaredConstructor.invoke(SecurityActionsTarget.class,
            new Class<?>[] {String.class, int.class });

        assertThat(constructor.getDeclaringClass()).isEqualTo(SecurityActionsTarget.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class);
    }

    @Test
    void setsFieldValueOnTargetObject() throws Throwable {
        Class<?> securityActions = securityActionsType();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(securityActions, MethodHandles.lookup());
        MethodHandle set = lookup.findStatic(securityActions, "set",
            MethodType.methodType(void.class, Field.class, Object.class, Object.class));
        SecurityActionsTarget target = new SecurityActionsTarget("initial", 1);
        Field field = SecurityActionsTarget.class.getField("publicValue");

        set.invoke(field, target, "updated");

        assertThat(target.publicValue).isEqualTo("updated");
    }

    private static Class<?> securityActionsType() throws ClassNotFoundException {
        return Class.forName("javassist.util.proxy.SecurityActions");
    }

    public static final class SecurityActionsTarget {
        public Object publicValue;

        public SecurityActionsTarget(String name, int count) {
            publicValue = name + count;
        }
    }
}
