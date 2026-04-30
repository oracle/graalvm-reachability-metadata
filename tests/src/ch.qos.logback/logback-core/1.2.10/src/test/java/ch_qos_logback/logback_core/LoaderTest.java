/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.util.Loader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Set;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LoaderTest {
    private static final String LOGBACK_CONTEXT_CLASS_NAME = ContextBase.class.getName();
    private static final String MISSING_RESOURCE_NAME = "ch/qos/logback/core/util/missing-test-resource.properties";

    @Test
    @Order(1)
    void returnsEmptySetWhenResourceIsAbsentFromSuppliedClassLoader() throws IOException {
        ClassLoader classLoader = LoaderTest.class.getClassLoader();

        Set<URL> resources = Loader.getResources(MISSING_RESOURCE_NAME, classLoader);

        assertThat(resources).isEmpty();
    }

    @Test
    @Order(2)
    void returnsNullWhenResourceIsAbsentFromSuppliedClassLoader() {
        ClassLoader classLoader = LoaderTest.class.getClassLoader();

        URL resource = Loader.getResource(MISSING_RESOURCE_NAME, classLoader);

        assertThat(resource).isNull();
    }

    @Test
    @Order(3)
    void loadsClassUsingContextObjectClassLoader() throws ClassNotFoundException {
        ContextBase context = new ContextBase();

        Class<?> loadedClass = Loader.loadClass(LOGBACK_CONTEXT_CLASS_NAME, context);

        assertThat(loadedClass).isEqualTo(ContextBase.class);
    }

    @Test
    @Order(4)
    void loadsClassUsingThreadContextClassLoader() throws ClassNotFoundException {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(LoaderTest.class.getClassLoader());
        try {
            Class<?> loadedClass = Loader.loadClass(LOGBACK_CONTEXT_CLASS_NAME);

            assertThat(loadedClass).isEqualTo(ContextBase.class);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    @Order(5)
    void fallsBackToClassForNameWhenThreadContextClassLoaderCannotLoadClass() throws ClassNotFoundException {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ClassLoader rejectingClassLoader = new RejectingClassLoader(originalClassLoader, LOGBACK_CONTEXT_CLASS_NAME);
        currentThread.setContextClassLoader(rejectingClassLoader);
        try {
            Class<?> loadedClass = Loader.loadClass(LOGBACK_CONTEXT_CLASS_NAME);

            assertThat(loadedClass).isEqualTo(ContextBase.class);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    @Order(6)
    void loadsClassWithClassForNameWhenThreadContextClassLoaderIsIgnored() throws Exception {
        Field ignoreTclField = Loader.class.getDeclaredField("ignoreTCL");
        ignoreTclField.setAccessible(true);
        boolean previousIgnoreTcl = ignoreTclField.getBoolean(null);
        ignoreTclField.setBoolean(null, true);
        try {
            Class<?> loadedClass = Loader.loadClass(LOGBACK_CONTEXT_CLASS_NAME);

            assertThat(loadedClass).isEqualTo(ContextBase.class);
        } finally {
            ignoreTclField.setBoolean(null, previousIgnoreTcl);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
