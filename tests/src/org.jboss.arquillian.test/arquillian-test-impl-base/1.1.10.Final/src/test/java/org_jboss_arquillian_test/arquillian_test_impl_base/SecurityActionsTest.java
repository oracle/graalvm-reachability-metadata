/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
        "org.jboss.arquillian.test.impl.enricher.resource.SecurityActions";

    @Test
    void loadClassUsesThreadContextClassLoaderFirst() throws Throwable {
        MethodHandle loadClass = securityActionsLookup().findStatic(securityActionsType(), "loadClass",
            MethodType.methodType(Class.class, String.class));

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(SecurityActionsTarget.class.getClassLoader());
        try {
            Class<?> loadedClass = (Class<?>) loadClass.invoke(SecurityActionsTarget.class.getName());

            assertThat(loadedClass).isEqualTo(SecurityActionsTarget.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToSecurityActionsClassLoaderWhenContextClassLoaderCannotLoadClass() throws Throwable {
        MethodHandle loadClass = securityActionsLookup().findStatic(securityActionsType(), "loadClass",
            MethodType.methodType(Class.class, String.class));

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            Class<?> loadedClass = (Class<?>) loadClass.invoke(SecurityActionsTarget.class.getName());

            assertThat(loadedClass).isEqualTo(SecurityActionsTarget.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void newInstanceWithClassLoaderLoadsClassAndInvokesConstructor() throws Throwable {
        MethodHandle newInstance = securityActionsLookup().findStatic(securityActionsType(), "newInstance",
            MethodType.methodType(Object.class, String.class, Class[].class, Object[].class, Class.class,
                ClassLoader.class));

        Object instance = newInstance.invoke(SecurityActionsTarget.class.getName(), new Class<?>[] {String.class},
            new Object[] {"created"}, SecurityActionsTarget.class, SecurityActionsTarget.class.getClassLoader());

        assertThat(instance).isInstanceOf(SecurityActionsTarget.class);
        SecurityActionsTarget target = (SecurityActionsTarget) instance;
        assertThat(target.name()).isEqualTo("created");
    }

    private static MethodHandles.Lookup securityActionsLookup() throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(securityActionsType(), MethodHandles.lookup());
    }

    private static Class<?> securityActionsType() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    public static final class SecurityActionsTarget {
        private final String name;

        private SecurityActionsTarget(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }

}
