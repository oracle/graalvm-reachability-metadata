/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.arquillian.test.impl.enricher.resource.ArquillianResourceTestEnricher;
import org.jboss.arquillian.test.spi.TestEnricher;
import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.test.impl.enricher.resource.SecurityActions";

    @Test
    void loadClassUsesThreadContextClassLoaderWhenItCanLoadTheType() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ArquillianResourceTestEnricher.class.getClassLoader());
        try {
            Class<?> loadedClass = (Class<?>) invokeSecurityAction(
                    "loadClass",
                    new Class<?>[] {String.class},
                    ArquillianResourceTestEnricher.class.getName());

            assertThat(loadedClass).isSameAs(ArquillianResourceTestEnricher.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void loadClassFallsBackWhenTcclCannotLoadTheType() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        ClassLoader rejectingClassLoader = new RejectingClassLoader(
                ArquillianResourceTestEnricher.class.getName());
        thread.setContextClassLoader(rejectingClassLoader);
        try {
            Class<?> loadedClass = (Class<?>) invokeSecurityAction(
                    "loadClass",
                    new Class<?>[] {String.class},
                    ArquillianResourceTestEnricher.class.getName());

            assertThat(loadedClass).isSameAs(ArquillianResourceTestEnricher.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void newInstanceLoadsTypeWithProvidedClassLoaderAndInvokesConstructor() throws Exception {
        Object enricher = invokeSecurityAction(
                "newInstance",
                new Class<?>[] {
                    String.class, Class[].class, Object[].class, Class.class, ClassLoader.class
                },
                ArquillianResourceTestEnricher.class.getName(),
                new Class<?>[0],
                new Object[0],
                TestEnricher.class,
                ArquillianResourceTestEnricher.class.getClassLoader());

        assertThat(enricher).isInstanceOf(ArquillianResourceTestEnricher.class);
        assertThat(enricher).isInstanceOf(TestEnricher.class);
    }

    private static Object invokeSecurityAction(
            String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Class<?> securityActions = Class.forName(SECURITY_ACTIONS_CLASS_NAME);
        Method method = securityActions.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, arguments);
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
