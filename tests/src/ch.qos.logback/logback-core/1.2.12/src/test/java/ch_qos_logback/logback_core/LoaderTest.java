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
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Set;
import java.util.ServiceLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class LoaderTest {

    private static final String TEST_CLASS_RESOURCE = LoaderTest.class.getName().replace('.', '/') + ".class";
    private static final String IGNORE_TCL_PROPERTY_NAME = Loader.IGNORE_TCL_PROPERTY_NAME;
    private static final String ISOLATED_PROVIDER_PACKAGE = "ch_qos_logback.logback_core.isolated.";
    private static final String LOGBACK_PACKAGE = "ch.qos.logback.";

    @BeforeAll
    static void clearIgnoreThreadContextClassLoaderProperty() {
        System.clearProperty(IGNORE_TCL_PROPERTY_NAME);
    }

    @Test
    void loadsResourcesAndClassesFromProvidedClassLoaders() throws Exception {
        ClassLoader classLoader = LoaderTest.class.getClassLoader();
        Set<URL> resources = Loader.getResources(TEST_CLASS_RESOURCE, classLoader);
        URL resource = Loader.getResource(TEST_CLASS_RESOURCE, classLoader);

        assertThat(resources).isNotEmpty();
        assertThat(resource).isNotNull();
        assertThat(resources).contains(resource);
        assertThat(Loader.loadClass(ContextBase.class.getName(), new ContextBase())).isEqualTo(ContextBase.class);
    }

    @Test
    void loadsClassThroughThreadContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(LoaderTest.class.getClassLoader());

        try {
            assertThat(Loader.loadClass(ContextBase.class.getName())).isEqualTo(ContextBase.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void fallsBackToClassForNameWhenThreadContextClassLoaderIsMissing() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(null);

        try {
            assertThat(Loader.loadClass(ContextBase.class.getName())).isEqualTo(ContextBase.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void canIgnoreThreadContextClassLoaderWhenConfigured() throws Exception {
        String previousValue = System.getProperty(IGNORE_TCL_PROPERTY_NAME);
        System.setProperty(IGNORE_TCL_PROPERTY_NAME, Boolean.TRUE.toString());

        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(new URL[] {
                codeSourceUrl(LoaderTest.class),
                codeSourceUrl(ContextBase.class)
        }, LoaderTest.class.getClassLoader())) {
            IsolatedLoaderAction action = ServiceLoader.load(IsolatedLoaderAction.class, classLoader)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected an isolated Loader action provider"));

            assertThat(action.loadClass(String.class.getName())).isEqualTo(String.class.getName());
        } finally {
            restoreSystemProperty(previousValue);
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static void restoreSystemProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(IGNORE_TCL_PROPERTY_NAME);
        } else {
            System.setProperty(IGNORE_TCL_PROPERTY_NAME, previousValue);
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
            return className.startsWith(LOGBACK_PACKAGE) || className.startsWith(ISOLATED_PROVIDER_PACKAGE);
        }
    }
}
