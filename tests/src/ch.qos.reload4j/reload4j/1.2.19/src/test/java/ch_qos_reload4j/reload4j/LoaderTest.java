/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;

import org.apache.log4j.helpers.Loader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(OrderAnnotation.class)
public class LoaderTest {
    private static final String IGNORE_TCL_PROPERTY = "log4j.ignoreTCL";
    private static String originalIgnoreTclProperty;

    @BeforeAll
    static void rememberLog4jIgnoreTclProperty() {
        originalIgnoreTclProperty = System.getProperty(IGNORE_TCL_PROPERTY);
        System.clearProperty(IGNORE_TCL_PROPERTY);
    }

    @AfterAll
    static void restoreLog4jIgnoreTclProperty() throws Exception {
        setIgnoreTcl(false);
        if (originalIgnoreTclProperty == null) {
            System.clearProperty(IGNORE_TCL_PROPERTY);
        } else {
            System.setProperty(IGNORE_TCL_PROPERTY, originalIgnoreTclProperty);
        }
    }

    @Test
    @Order(1)
    void getResourceSearchesContextLoaderApplicationLoaderAndSystemLoader() throws Exception {
        ResourceHidingClassLoader contextClassLoader = new ResourceHidingClassLoader();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            URL resource = Loader.getResource("reload4j-loader-test-resource-that-does-not-exist.properties");

            assertThat(resource).isNull();
            assertThat(contextClassLoader.getResourceCalls).isEqualTo(1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    @Order(2)
    void loadClassUsesContextClassLoaderWhenItCanLoadTheClass() throws Exception {
        ContextClassLoader contextClassLoader = new ContextClassLoader(true);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            Class loadedClass = Loader.loadClass(Properties.class.getName());

            assertThat(loadedClass).isEqualTo(Properties.class);
            assertThat(contextClassLoader.loadClassCalls).isEqualTo(1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    @Order(3)
    void loadClassFallsBackToClassForNameWhenContextClassLoaderCannotLoadTheClass() throws Exception {
        ContextClassLoader contextClassLoader = new ContextClassLoader(false);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            Class loadedClass = Loader.loadClass(ArrayList.class.getName());

            assertThat(loadedClass).isEqualTo(ArrayList.class);
            assertThat(contextClassLoader.loadClassCalls).isEqualTo(1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    @Order(4)
    void loadClassCanIgnoreContextClassLoader() throws Exception {
        ContextClassLoader contextClassLoader = new ContextClassLoader(false);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            setIgnoreTcl(true);
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            Class loadedClass = Loader.loadClass(Properties.class.getName());

            assertThat(loadedClass).isEqualTo(Properties.class);
            assertThat(contextClassLoader.loadClassCalls).isZero();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            setIgnoreTcl(false);
        }
    }

    private static void setIgnoreTcl(boolean ignoreTcl) throws Exception {
        Field field = Loader.class.getDeclaredField("ignoreTCL");
        field.setAccessible(true);
        field.setBoolean(null, ignoreTcl);
    }

    private static final class ResourceHidingClassLoader extends ClassLoader {
        private int getResourceCalls;

        @Override
        public URL getResource(String name) {
            getResourceCalls++;
            return null;
        }
    }

    private static final class ContextClassLoader extends ClassLoader {
        private final boolean canLoadClass;
        private int loadClassCalls;

        private ContextClassLoader(boolean canLoadClass) {
            this.canLoadClass = canLoadClass;
        }

        @Override
        public Class loadClass(String name) throws ClassNotFoundException {
            loadClassCalls++;
            if (canLoadClass && Properties.class.getName().equals(name)) {
                return Properties.class;
            }
            throw new ClassNotFoundException(name);
        }
    }
}
