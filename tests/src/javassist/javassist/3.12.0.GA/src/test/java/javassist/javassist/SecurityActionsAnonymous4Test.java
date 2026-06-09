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
import java.security.Permission;

import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
public class SecurityActionsAnonymous4Test {
    @Test
    void getsDeclaredConstructorWithSecurityManagerInstalled() throws Throwable {
        SecurityManager originalManager = System.getSecurityManager();
        SecurityManager manager = null;
        boolean installed = false;
        try {
            try {
                manager = new PermissiveSecurityManager();
                System.setSecurityManager(manager);
                installed = System.getSecurityManager() == manager;
            } catch (UnsupportedOperationException exception) {
                assertThat(exception).isInstanceOf(UnsupportedOperationException.class);
            }

            Constructor<?> constructor = getDeclaredConstructor(ConstructorTarget.class,
                    new Class<?>[] { String.class, int.class });

            assertThat(constructor.getDeclaringClass()).isEqualTo(ConstructorTarget.class);
            assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class);
        } finally {
            if (installed) {
                System.setSecurityManager(originalManager);
            }
        }
    }

    private static Constructor<?> getDeclaredConstructor(
            Class<?> targetClass, Class<?>[] parameterTypes) throws Throwable {
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                securityActions, MethodHandles.lookup());
        MethodHandle getDeclaredConstructor = lookup.findStatic(
                securityActions, "getDeclaredConstructor",
                MethodType.methodType(Constructor.class, Class.class, Class[].class));
        return (Constructor<?>) getDeclaredConstructor.invoke(targetClass, parameterTypes);
    }

    private static final class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
            // Permit all operations while the Javassist security-manager branch is exercised.
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
            // Permit all operations while the Javassist security-manager branch is exercised.
        }
    }

    public static final class ConstructorTarget {
        public ConstructorTarget(String name, int count) {
            assertThat(name).isNotNull();
            assertThat(count).isNotNegative();
        }
    }
}
