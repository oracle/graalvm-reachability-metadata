/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.arquillian.container.test.spi.TestRunner;
import org.jboss.arquillian.container.test.spi.util.TestRunners;
import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.container.test.spi.util.SecurityActions";

    @Test
    void loadClassUsesThreadContextClassLoaderWhenClassIsVisible() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(TestRunner.class.getClassLoader());
        try {
            Class<?> loadedClass = invokeLoadClass(TestRunner.class.getName());

            assertThat(loadedClass).isSameAs(TestRunner.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToSecurityActionsClassLoaderWhenContextClassLoaderCannotLoadClass() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RejectingClassLoader());
        try {
            Class<?> loadedClass = invokeLoadClass(TestRunner.class.getName());

            assertThat(loadedClass).isSameAs(TestRunner.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void newInstanceUsesProvidedClassLoaderAndConstructorArguments() throws Exception {
        Object instance = invokeNewInstance(
                StringBuilder.class.getName(),
                new Class<?>[] {String.class},
                new Object[] {"arquillian"},
                CharSequence.class,
                StringBuilder.class.getClassLoader());

        assertThat(instance)
                .isInstanceOf(StringBuilder.class)
                .hasToString("arquillian");
    }

    private static Class<?> invokeLoadClass(String className) throws Exception {
        Method method = securityActionsClass().getDeclaredMethod("loadClass", String.class);
        method.setAccessible(true);
        return (Class<?>) method.invoke(null, className);
    }

    private static Object invokeNewInstance(
            String className,
            Class<?>[] argumentTypes,
            Object[] arguments,
            Class<?> expectedType,
            ClassLoader classLoader) throws Exception {
        Method method = securityActionsClass().getDeclaredMethod(
                "newInstance",
                String.class,
                Class[].class,
                Object[].class,
                Class.class,
                ClassLoader.class);
        method.setAccessible(true);
        return method.invoke(
                null,
                new Object[] {className, argumentTypes, arguments, expectedType, classLoader});
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return TestRunners.class.getClassLoader().loadClass(SECURITY_ACTIONS_CLASS_NAME);
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
