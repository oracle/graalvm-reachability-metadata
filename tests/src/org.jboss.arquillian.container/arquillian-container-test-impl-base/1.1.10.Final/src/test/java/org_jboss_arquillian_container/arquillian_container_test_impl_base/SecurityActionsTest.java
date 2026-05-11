/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.lang.reflect.Method;

import org.jboss.arquillian.container.test.impl.RemoteExtensionLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsTest {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.container.test.impl.SecurityActions";

    @Test
    void loadClassUsesThreadContextClassLoaderWhenClassIsAvailable() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(SecurityActionsTest.class.getClassLoader());

            Class<?> loadedClass = invokeLoadClass(RemoteExtensionLoader.class.getName());

            assertThat(loadedClass).isSameAs(RemoteExtensionLoader.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToSecurityActionsClassLoaderWhenTcclCannotLoadClass() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new BlockingClassLoader(RemoteExtensionLoader.class.getName()));

            Class<?> loadedClass = invokeLoadClass(RemoteExtensionLoader.class.getName());

            assertThat(loadedClass).isSameAs(RemoteExtensionLoader.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void newInstanceLoadsClassWithProvidedClassLoaderAndInvokesConstructor() throws Exception {
        RemoteExtensionLoader instance = invokeNewInstance(
                RemoteExtensionLoader.class.getName(),
                new Class<?>[0],
                new Object[0],
                RemoteExtensionLoader.class,
                RemoteExtensionLoader.class.getClassLoader());

        assertThat(instance).isNotNull();
    }

    private static Class<?> invokeLoadClass(String className) throws Exception {
        Method loadClassMethod = securityActionsClass().getDeclaredMethod("loadClass", String.class);
        loadClassMethod.setAccessible(true);
        return (Class<?>) loadClassMethod.invoke(null, className);
    }

    private static <T> T invokeNewInstance(
            String className,
            Class<?>[] argumentTypes,
            Object[] arguments,
            Class<T> expectedType,
            ClassLoader classLoader) throws Exception {
        Method newInstanceMethod = securityActionsClass().getDeclaredMethod(
                "newInstance",
                String.class,
                Class[].class,
                Object[].class,
                Class.class,
                ClassLoader.class);
        newInstanceMethod.setAccessible(true);
        Object instance = newInstanceMethod.invoke(
                null,
                className,
                argumentTypes,
                arguments,
                expectedType,
                classLoader);
        return expectedType.cast(instance);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return RemoteExtensionLoader.class.getClassLoader().loadClass(SECURITY_ACTIONS_CLASS_NAME);
    }

    private static final class BlockingClassLoader extends ClassLoader {
        private final String blockedClassName;

        private BlockingClassLoader(String blockedClassName) {
            super(null);
            this.blockedClassName = blockedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (blockedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}

