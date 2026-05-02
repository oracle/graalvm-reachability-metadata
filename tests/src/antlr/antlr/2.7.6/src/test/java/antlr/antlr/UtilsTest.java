/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
    @Test
    void loadsClassDirectlyWhenContextClassLoaderIsUnavailable() throws ClassNotFoundException {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(null);
        try {
            Class<?> loadedClass = Utils.loadClass(String.class.getName());

            assertThat(loadedClass).isSameAs(String.class);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToDirectClassLoadingWhenContextClassLoaderFails() throws ClassNotFoundException {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new FailingClassLoader(originalClassLoader));
        try {
            Class<?> loadedClass = Utils.loadClass(String.class.getName());

            assertThat(loadedClass).isSameAs(String.class);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createsInstanceByClassName() throws Exception {
        Object instance = Utils.createInstanceOf(String.class.getName());

        assertThat(instance).isEqualTo("");
    }

    private static final class FailingClassLoader extends ClassLoader {
        private FailingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
