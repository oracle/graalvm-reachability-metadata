/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.arquillian.container.test.spi.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsTest {
    @Test
    void loadClassUsesThreadContextClassLoaderWhenClassIsAvailable() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(TestRunners.class.getClassLoader());

            Class<?> loadedClass = SecurityActions.loadClass(TestRunners.class.getName());

            assertThat(loadedClass).isSameAs(TestRunners.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToSecurityActionsClassLoaderWhenTcclCannotLoadClass() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new BlockingClassLoader(TestRunners.class.getName()));

            Class<?> loadedClass = SecurityActions.loadClass(TestRunners.class.getName());

            assertThat(loadedClass).isSameAs(TestRunners.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void newInstanceLoadsClassWithProvidedClassLoaderAndInvokesConstructor() throws Exception {
        TestRunners instance = SecurityActions.newInstance(
                TestRunners.class.getName(),
                new Class<?>[0],
                new Object[0],
                TestRunners.class,
                TestRunners.class.getClassLoader());

        assertThat(instance).isInstanceOf(TestRunners.class);
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
