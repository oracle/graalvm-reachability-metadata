/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import java.net.URL;

import bsh.Interpreter;
import bsh.classpath.ClassManagerImpl;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassManagerImplTest {
    private static final String FALLBACK_RESOURCE = "/org_apache_extras_beanshell/bsh/bsh-class-manager-resource.txt";

    @Test
    void resolvesBeanshellCoreClassesWithInterpreterClassLoader() throws Exception {
        ClassManagerImpl classManager = new ClassManagerImpl();

        assertSupportedDynamicClassLoading(() -> {
            Class<?> resolvedClass = classManager.classForName(Interpreter.class.getName());

            assertThat(resolvedClass).isSameAs(Interpreter.class);
        });
    }

    @Test
    void resolvesClassesThroughConfiguredBaseLoader() throws Exception {
        ClassManagerImpl classManager = new ClassManagerImpl();
        classManager.setClassPath(new URL[0]);

        assertSupportedDynamicClassLoading(() -> {
            Class<?> resolvedClass = classManager.classForName(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
        });
    }

    @Test
    void resolvesClassesThroughExternalClassLoader() throws Exception {
        ClassManagerImpl classManager = new ClassManagerImpl();
        SingleClassLoader classLoader = new SingleClassLoader();
        classManager.setClassLoader(classLoader);

        assertSupportedDynamicClassLoading(() -> {
            Class<?> resolvedClass = classManager.classForName(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
            assertThat(classLoader.loadAttempts()).isEqualTo(1);
        });
    }

    @Test
    void consultsBaseLoaderWhenResolvingResources() {
        ClassManagerImpl classManager = new ClassManagerImpl();
        classManager.setClassPath(new URL[0]);

        URL resource = classManager.getResource(FALLBACK_RESOURCE);

        assertThat(resource).isNotNull();
    }

    private static void assertSupportedDynamicClassLoading(ThrowingRunnable action) throws Exception {
        try {
            action.run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class SingleClassLoader extends ClassLoader {
        private int loadAttempts;

        private SingleClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadAttempts++;
            if (String.class.getName().equals(name)) {
                return String.class;
            }
            throw new ClassNotFoundException(name);
        }

        private int loadAttempts() {
            return loadAttempts;
        }
    }
}
