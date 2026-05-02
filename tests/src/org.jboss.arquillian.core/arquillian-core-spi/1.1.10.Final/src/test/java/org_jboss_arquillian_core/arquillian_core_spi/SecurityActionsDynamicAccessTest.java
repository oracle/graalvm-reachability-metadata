/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_spi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Collections;

import org.jboss.arquillian.core.spi.Manager;
import org.jboss.arquillian.core.spi.ManagerBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsDynamicAccessTest {
    private static final String SECURITY_ACTIONS_CLASS = "org.jboss.arquillian.core.spi.SecurityActions";
    private static final String MANAGER_IMPL_CLASS = "org.jboss.arquillian.core.impl.ManagerImpl";

    @Test
    void createsManagerThroughDefaultThreadContextClassLoader() {
        Manager manager = ManagerBuilder.from().create();

        assertThat(manager).isNotNull();
    }

    @Test
    void createsManagerAfterFallingBackFromThreadContextClassLoader() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new ManagerImplBlockingClassLoader(originalContextClassLoader));
        try {
            Manager manager = ManagerBuilder.from().create();

            assertThat(manager).isNotNull();
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void createsManagerThroughExplicitClassLoaderPath() throws Throwable {
        MethodHandle explicitClassLoaderNewInstance = explicitClassLoaderNewInstanceMethod();

        Object created = explicitClassLoaderNewInstance.invoke(
                MANAGER_IMPL_CLASS,
                new Class<?>[] {Collection.class, Collection.class},
                new Object[] {Collections.emptySet(), Collections.emptySet()},
                Manager.class,
                ManagerBuilder.class.getClassLoader());

        assertThat(created).isInstanceOf(Manager.class);
    }

    private static MethodHandle explicitClassLoaderNewInstanceMethod() throws Exception {
        ClassLoader classLoader = ManagerBuilder.class.getClassLoader();
        Class<?> securityActionsClass = (classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader)
                .loadClass(SECURITY_ACTIONS_CLASS);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(securityActionsClass, MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(
                Object.class,
                String.class,
                Class[].class,
                Object[].class,
                Class.class,
                ClassLoader.class);
        return lookup.findStatic(securityActionsClass, "newInstance", methodType);
    }

    private static final class ManagerImplBlockingClassLoader extends ClassLoader {
        private final ClassLoader delegate;

        private ManagerImplBlockingClassLoader(ClassLoader delegate) {
            super(null);
            this.delegate = delegate == null ? ClassLoader.getSystemClassLoader() : delegate;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (MANAGER_IMPL_CLASS.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            Class<?> loadedClass = delegate.loadClass(name);
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }
}
