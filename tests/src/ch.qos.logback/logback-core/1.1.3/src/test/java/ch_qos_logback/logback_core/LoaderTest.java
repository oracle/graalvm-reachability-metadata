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
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class LoaderTest {

    private static final String TEST_CLASS_RESOURCE = LoaderTest.class.getName().replace('.', '/') + ".class";
    private static final String IGNORE_TCL_PROPERTY_NAME = "logback.ignoreTCL";
    private static final String LOGBACK_PACKAGE = "ch.qos.logback.";
    private static final String ISOLATED_PROVIDER_PACKAGE = "ch_qos_logback.logback_core.isolated.";

    @BeforeAll
    static void clearIgnoreThreadContextClassLoaderProperty() {
        System.clearProperty(IGNORE_TCL_PROPERTY_NAME);
    }

    @AfterEach
    void clearIgnoreThreadContextClassLoaderPropertyAfterEachTest() {
        System.clearProperty(IGNORE_TCL_PROPERTY_NAME);
    }

    @Test
    void loadsResourcesFromProvidedClassLoader() throws Exception {
        ClassLoader classLoader = LoaderTest.class.getClassLoader();
        Set<URL> resources = Loader.getResourceOccurrenceCount(TEST_CLASS_RESOURCE, classLoader);
        URL resource = Loader.getResource(TEST_CLASS_RESOURCE, classLoader);

        assertThat(resources).isNotEmpty();
        assertThat(resource).isNotNull();
        assertThat(resources).contains(resource);
    }

    @Test
    void loadsClassesFromContextAndProvidedClassLoaders() throws Exception {
        assertThat(Loader.loadClass(ContextBase.class.getName(), new ContextBase())).isEqualTo(ContextBase.class);

        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader(
                LoaderTest.class.getClassLoader(),
                Set.of(ContextBase.class.getName())
        );
        thread.setContextClassLoader(trackingClassLoader);

        try {
            assertThat(Loader.loadClass(ContextBase.class.getName())).isEqualTo(ContextBase.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }

        assertThat(trackingClassLoader.getDelegatedLoads()).contains(ContextBase.class.getName());
    }

    @Test
    void fallsBackToClassForNameWhenContextClassLoaderCannotLoadTheClass() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(
                LoaderTest.class.getClassLoader(),
                Set.of(ContextBase.class.getName())
        );
        thread.setContextClassLoader(rejectingClassLoader);

        try {
            assertThat(Loader.loadClass(ContextBase.class.getName())).isEqualTo(ContextBase.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }

        assertThat(rejectingClassLoader.getRejectedLoads()).contains(ContextBase.class.getName());
    }

    @Test
    void canIgnoreTheThreadContextClassLoaderWhenConfiguredBeforeLoaderInitialization() throws Exception {
        System.setProperty(IGNORE_TCL_PROPERTY_NAME, Boolean.TRUE.toString());

        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(new URL[] {
                codeSourceUrl(LoaderTest.class),
                codeSourceUrl(ContextBase.class)
        }, LoaderTest.class.getClassLoader())) {
            IsolatedLoaderAction action = ServiceLoader.load(IsolatedLoaderAction.class, classLoader)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected an isolated Loader action"));

            assertThat(action.loadStringClass()).isEqualTo(String.class.getName());
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();

        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static final class TrackingClassLoader extends ClassLoader {

        private final Set<String> delegatedLoads = new LinkedHashSet<>();
        private final Set<String> trackedClassNames;

        private TrackingClassLoader(ClassLoader parent, Set<String> trackedClassNames) {
            super(parent);
            this.trackedClassNames = trackedClassNames;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (trackedClassNames.contains(name)) {
                delegatedLoads.add(name);
            }
            return super.loadClass(name);
        }

        private Set<String> getDelegatedLoads() {
            return delegatedLoads;
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {

        private final Set<String> rejectedLoads = new LinkedHashSet<>();
        private final Set<String> rejectedClassNames;

        private RejectingClassLoader(ClassLoader parent, Set<String> rejectedClassNames) {
            super(parent);
            this.rejectedClassNames = rejectedClassNames;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedClassNames.contains(name)) {
                rejectedLoads.add(name);
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }

        private Set<String> getRejectedLoads() {
            return rejectedLoads;
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
