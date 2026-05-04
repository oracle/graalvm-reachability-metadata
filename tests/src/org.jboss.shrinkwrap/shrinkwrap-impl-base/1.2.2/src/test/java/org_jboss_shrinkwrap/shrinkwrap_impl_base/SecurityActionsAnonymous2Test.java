/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous2Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.shrinkwrap.impl.base.SecurityActions";

    @Test
    void getConstructorReturnsPublicConstructorMatchingArgumentTypes() throws Throwable {
        MethodHandle getConstructor = securityActionsLookup().findStatic(securityActionsType(), "getConstructor",
            MethodType.methodType(Constructor.class, Class.class, Class[].class));

        Constructor<?> constructor = (Constructor<?>) getConstructor.invoke(ConstructorTarget.class,
            new Class<?>[] {String.class, int.class });

        assertThat(constructor.getDeclaringClass()).isEqualTo(ConstructorTarget.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class);
    }

    private static MethodHandles.Lookup securityActionsLookup() throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(securityActionsType(), MethodHandles.lookup());
    }

    private static Class<?> securityActionsType() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    public static final class ConstructorTarget {
        public ConstructorTarget(String name, int count) {
        }
    }
}
