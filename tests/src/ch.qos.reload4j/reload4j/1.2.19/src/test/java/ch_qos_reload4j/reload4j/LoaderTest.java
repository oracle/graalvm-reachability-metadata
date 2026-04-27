/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ServiceLoader;

import org.apache.log4j.helpers.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class LoaderTest {
    private static final String IGNORE_THREAD_CONTEXT_CLASS_LOADER_PROPERTY = "log4j.ignoreTCL";
    private static final String LOG4J_PACKAGE = "org.apache.log4j.";
    private static final String ISOLATED_PROVIDER_PACKAGE = "ch_qos_reload4j.reload4j.isolated.";
    private static final String ISOLATED_PROVIDER_CLASS = ISOLATED_PROVIDER_PACKAGE + "IsolatedLoaderActionProvider";
    private static final String TEMPORARY_RESOURCE = "reload4j-loader-context-resource.txt";

    @BeforeAll
    static void clearIgnoreThreadContextClassLoaderProperty() {
        System.clearProperty(IGNORE_THREAD_CONTEXT_CLASS_LOADER_PROPERTY);
    }

    @Test
    void findsResourceThroughThreadContextClassLoader(@TempDir Path temporaryDirectory) throws Exception {
        Files.writeString(temporaryDirectory.resolve(TEMPORARY_RESOURCE), "loader-resource");
        try (URLClassLoader contextClassLoader = new URLClassLoader(
                new URL[] {temporaryDirectory.toUri().toURL() }, null)) {
            URL resource = withContextClassLoader(contextClassLoader, () -> Loader.getResource(TEMPORARY_RESOURCE));

            assertThat(resource).isNotNull();
            assertThat(resource.toString()).endsWith(TEMPORARY_RESOURCE);
        }
    }

    @Test
    void fallsBackToSystemResourceLookupWhenResourceIsMissing() throws Exception {
        URL resource = withContextClassLoader(new EmptyResourceClassLoader(),
                () -> Loader.getResource("reload4j-loader-missing-resource.txt"));

        assertThat(resource).isNull();
    }

    @Test
    void loadsClassThroughThreadContextClassLoader() throws Exception {
        Class<?> loadedClass = withContextClassLoader(LoaderTest.class.getClassLoader(),
                () -> Loader.loadClass(LoaderTest.class.getName()));

        assertThat(loadedClass).isEqualTo(LoaderTest.class);
    }

    @Test
    void fallsBackToClassForNameWhenThreadContextClassLoaderCannotLoadClass() throws Exception {
        Class<?> loadedClass = withContextClassLoader(new RejectingClassLoader(),
                () -> Loader.loadClass(String.class.getName()));

        assertThat(loadedClass).isEqualTo(String.class);
    }

    @Test
    void canIgnoreThreadContextClassLoaderWhenConfigured() throws Exception {
        String previousValue = System.getProperty(IGNORE_THREAD_CONTEXT_CLASS_LOADER_PROPERTY);
        System.setProperty(IGNORE_THREAD_CONTEXT_CLASS_LOADER_PROPERTY, Boolean.TRUE.toString());

        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(new URL[] {
                codeSourceUrl(LoaderTest.class),
                codeSourceUrl(Loader.class)
        }, LoaderTest.class.getClassLoader())) {
            IsolatedLoaderAction action = findIsolatedLoaderAction(classLoader);

            assertThat(action.loadClass(String.class.getName())).isEqualTo(String.class.getName());
        } finally {
            restoreSystemProperty(previousValue);
        }
    }

    private static IsolatedLoaderAction findIsolatedLoaderAction(ClassLoader classLoader) {
        for (IsolatedLoaderAction action : ServiceLoader.load(IsolatedLoaderAction.class, classLoader)) {
            if (action.getClass().getName().equals(ISOLATED_PROVIDER_CLASS)) {
                return action;
            }
        }
        throw new AssertionError("Expected an isolated Loader action provider");
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static void restoreSystemProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(IGNORE_THREAD_CONTEXT_CLASS_LOADER_PROPERTY);
        } else {
            System.setProperty(IGNORE_THREAD_CONTEXT_CLASS_LOADER_PROPERTY, previousValue);
        }
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, ThrowingSupplier<T> action) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return action.get();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class EmptyResourceClassLoader extends ClassLoader {
        private EmptyResourceClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            return null;
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {
        private ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (isChildFirst(name)) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            loadedClass = super.loadClass(name, false);
                        }
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private boolean isChildFirst(String className) {
            return className.startsWith(LOG4J_PACKAGE) || className.startsWith(ISOLATED_PROVIDER_PACKAGE);
        }
    }
}
