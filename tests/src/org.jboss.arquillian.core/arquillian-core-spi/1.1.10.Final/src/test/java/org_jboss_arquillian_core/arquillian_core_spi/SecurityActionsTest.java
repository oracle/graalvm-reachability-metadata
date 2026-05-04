/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_spi;

import org.jboss.arquillian.core.spi.Manager;
import org.jboss.arquillian.core.spi.ManagerBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsTest {
    private static final String MANAGER_IMPL_CLASS_NAME = "org.jboss.arquillian.core.impl.ManagerImpl";

    @Test
    void createsManagerWithContextClassLoader() {
        Manager manager = ManagerBuilder.from().create();

        try {
            assertThat(manager).isNotNull();
            assertThat(manager.getClass().getName()).isEqualTo(MANAGER_IMPL_CLASS_NAME);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void createsManagerWithExplicitClassLoader() throws Exception {
        Method newInstance = Class.forName("org.jboss.arquillian.core.spi.SecurityActions").getDeclaredMethod(
                "newInstance",
                String.class,
                Class[].class,
                Object[].class,
                Class.class,
                ClassLoader.class);
        newInstance.setAccessible(true);

        Manager manager = null;
        try {
            manager = (Manager) newInstance.invoke(
                    null,
                    MANAGER_IMPL_CLASS_NAME,
                    new Class<?>[] {Collection.class, Collection.class},
                    new Object[] {Set.of(), Set.of()},
                    Manager.class,
                    getClass().getClassLoader());

            assertThat(manager).isNotNull();
            assertThat(manager.getClass().getName()).isEqualTo(MANAGER_IMPL_CLASS_NAME);
        } finally {
            if (manager != null) {
                manager.shutdown();
            }
        }
    }

    @Test
    void createsManagerWhenContextClassLoaderCannotLoadImplementation() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new BlockingClassLoader(originalContextClassLoader));

        Manager manager = null;
        try {
            manager = ManagerBuilder.from().create();
            assertThat(manager).isNotNull();
            assertThat(manager.getClass().getName()).isEqualTo(MANAGER_IMPL_CLASS_NAME);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            if (manager != null) {
                manager.shutdown();
            }
        }
    }

    private static final class BlockingClassLoader extends ClassLoader {
        private BlockingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (MANAGER_IMPL_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
