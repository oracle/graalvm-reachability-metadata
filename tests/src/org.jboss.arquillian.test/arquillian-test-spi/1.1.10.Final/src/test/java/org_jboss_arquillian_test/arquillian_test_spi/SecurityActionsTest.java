/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.arquillian.test.spi.TestRunnerAdaptor;
import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.arquillian.test.spi.SecurityActions";
    private static final String TEST_RUNNER_ADAPTOR_CLASS_NAME = TestRunnerAdaptor.class.getName();
    private static final String CONSTRUCTED_SERVICE_CLASS_NAME = ConstructedService.class.getName();

    @Test
    void loadClassUsesThreadContextClassLoaderWhenClassIsAvailable() throws Exception {
        Class<?> loadedClass = invokeLoadClass(TEST_RUNNER_ADAPTOR_CLASS_NAME);

        assertThat(loadedClass).isSameAs(TestRunnerAdaptor.class);
    }

    @Test
    void loadClassFallsBackToSecurityActionsClassLoaderWhenThreadContextClassLoaderCannotLoadClass() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new RejectingClassLoader(TEST_RUNNER_ADAPTOR_CLASS_NAME));
        try {
            Class<?> loadedClass = invokeLoadClass(TEST_RUNNER_ADAPTOR_CLASS_NAME);

            assertThat(loadedClass).isSameAs(TestRunnerAdaptor.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void newInstanceLoadsClassWithProvidedClassLoaderAndInvokesConstructor() throws Exception {
        Method newInstance = securityActionsClass().getDeclaredMethod(
                "newInstance", String.class, Class[].class, Object[].class, Class.class, ClassLoader.class);
        newInstance.setAccessible(true);

        ConstructedService instance = (ConstructedService) newInstance.invoke(
                null,
                CONSTRUCTED_SERVICE_CLASS_NAME,
                new Class<?>[] {String.class, int.class},
                new Object[] {"created", 42},
                ConstructedService.class,
                SecurityActionsTest.class.getClassLoader());

        assertThat(instance.name()).isEqualTo("created");
        assertThat(instance.value()).isEqualTo(42);
    }

    private static Class<?> invokeLoadClass(String className) throws Exception {
        Method loadClass = securityActionsClass().getDeclaredMethod("loadClass", String.class);
        loadClass.setAccessible(true);
        return (Class<?>) loadClass.invoke(null, className);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    public static final class ConstructedService {
        private final String name;
        private final int value;

        public ConstructedService(String name, int value) {
            this.name = name;
            this.value = value;
        }

        private String name() {
            return name;
        }

        private int value() {
            return value;
        }
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
