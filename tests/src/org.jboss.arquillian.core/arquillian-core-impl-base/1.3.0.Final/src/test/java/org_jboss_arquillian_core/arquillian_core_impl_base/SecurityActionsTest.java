/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.arquillian.core.impl.context.ApplicationContextImpl;
import org.jboss.arquillian.core.spi.context.ApplicationContext;
import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.arquillian.core.impl.SecurityActions";
    private static final String APPLICATION_CONTEXT_IMPL_CLASS_NAME = ApplicationContextImpl.class.getName();

    @Test
    void loadClassUsesThreadContextClassLoaderWhenClassIsAvailable() throws Exception {
        Class<?> loadedClass = invokeLoadClass(APPLICATION_CONTEXT_IMPL_CLASS_NAME);

        assertThat(loadedClass).isSameAs(ApplicationContextImpl.class);
    }

    @Test
    void loadClassFallsBackToSecurityActionsClassLoaderWhenThreadContextClassLoaderCannotLoadClass() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new RejectingClassLoader(APPLICATION_CONTEXT_IMPL_CLASS_NAME));
        try {
            Class<?> loadedClass = invokeLoadClass(APPLICATION_CONTEXT_IMPL_CLASS_NAME);

            assertThat(loadedClass).isSameAs(ApplicationContextImpl.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void newInstanceLoadsClassWithProvidedClassLoaderAndInvokesConstructor() throws Exception {
        Method newInstance = securityActionsClass().getDeclaredMethod(
                "newInstance", String.class, Class[].class, Object[].class, Class.class, ClassLoader.class);
        newInstance.setAccessible(true);

        Object instance = newInstance.invoke(null, APPLICATION_CONTEXT_IMPL_CLASS_NAME, new Class<?>[0], new Object[0],
                ApplicationContext.class, ApplicationContextImpl.class.getClassLoader());

        assertThat(instance).isInstanceOf(ApplicationContextImpl.class);
        assertThat(instance).isInstanceOf(ApplicationContext.class);
    }

    private static Class<?> invokeLoadClass(String className) throws Exception {
        Method loadClass = securityActionsClass().getDeclaredMethod("loadClass", String.class);
        loadClass.setAccessible(true);
        return (Class<?>) loadClass.invoke(null, className);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(String rejectedClassName) {
            super(null);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return SecurityActionsTest.class.getClassLoader().loadClass(name);
        }
    }
}
