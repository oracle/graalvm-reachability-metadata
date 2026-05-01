/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.arquillian.core.spi.Manager;
import org.jboss.arquillian.core.spi.ManagerBuilder;
import org.jboss.arquillian.core.spi.SecurityActionsInvoker;
import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    @Test
    void managerBuilderCreatesManagerWithDefaultThreadContextClassLoader() {
        assertManagerCanBeCreated();
    }

    @Test
    void managerBuilderFallsBackToSpiClassLoaderWhenThreadContextClassLoaderCannotLoadImplementation() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RejectingClassLoader());
        try {
            assertManagerCanBeCreated();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void securityActionsCreatesManagerWithExplicitClassLoader() {
        ClassLoader classLoader = SecurityActionsTest.class.getClassLoader();
        Manager manager = SecurityActionsInvoker.createManagerWithClassLoader(classLoader);
        try {
            assertThat(manager).isNotNull();
        } finally {
            manager.shutdown();
        }
    }

    private static void assertManagerCanBeCreated() {
        Manager manager = ManagerBuilder.from().create();
        try {
            assertThat(manager).isNotNull();
        } finally {
            manager.shutdown();
        }
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
