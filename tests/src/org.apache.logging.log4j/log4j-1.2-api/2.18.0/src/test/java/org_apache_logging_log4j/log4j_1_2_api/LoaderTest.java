/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Callable;
import org.apache.log4j.helpers.Loader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class LoaderTest {

    private static final String IGNORE_TCL_PROPERTY_NAME = "log4j.ignoreTCL";
    private static final String TEST_RESOURCE = "org_apache_logging_log4j/log4j_1_2_api/loader-test-resource.txt";
    private static final String MISSING_RESOURCE = "org_apache_logging_log4j/log4j_1_2_api/missing-loader-resource.txt";
    private static final String LOG4J1_PACKAGE = "org.apache.log4j.";
    private static final String CALLABLE_SERVICE_RESOURCE = "META-INF/services/java.util.concurrent.Callable";
    private static final String ISOLATED_ACTION_CLASS_NAME = Log4jLoaderIsolatedAction.class.getName();

    @BeforeAll
    static void initializeLoaderWithThreadContextClassLoaderEnabled() {
        System.clearProperty(IGNORE_TCL_PROPERTY_NAME);

        assertThat(Loader.isJava1()).isFalse();
    }

    @Test
    void findsResourceWithThreadContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        TrackingResourceClassLoader classLoader = new TrackingResourceClassLoader(
                LoaderTest.class.getClassLoader(),
                TEST_RESOURCE
        );
        thread.setContextClassLoader(classLoader);

        URL resource;
        try {
            resource = Loader.getResource(TEST_RESOURCE);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }

        assertThat(resource).isNotNull();
        assertThat(classLoader.getRequestedResources()).contains(TEST_RESOURCE);
    }

    @Test
    void fallsBackToLoaderClassLoaderWhenThreadContextClassLoaderCannotFindResource() {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        RejectingResourceClassLoader classLoader = new RejectingResourceClassLoader(
                LoaderTest.class.getClassLoader(),
                Set.of(TEST_RESOURCE)
        );
        thread.setContextClassLoader(classLoader);

        URL resource;
        try {
            resource = Loader.getResource(TEST_RESOURCE);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }

        assertThat(resource).isNotNull();
        assertThat(classLoader.getRejectedResources()).contains(TEST_RESOURCE);
    }

    @Test
    void fallsBackToSystemResourceLookupWhenClassLoadersCannotFindResource() {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        RejectingResourceClassLoader classLoader = new RejectingResourceClassLoader(
                LoaderTest.class.getClassLoader(),
                Set.of(MISSING_RESOURCE)
        );
        thread.setContextClassLoader(classLoader);

        URL resource;
        try {
            resource = Loader.getResource(MISSING_RESOURCE);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }

        assertThat(resource).isNull();
        assertThat(classLoader.getRejectedResources()).contains(MISSING_RESOURCE);
    }

    @Test
    void loadsClassThroughThreadContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        TrackingClassLoader classLoader = new TrackingClassLoader(
                LoaderTest.class.getClassLoader(),
                Set.of(String.class.getName())
        );
        thread.setContextClassLoader(classLoader);

        Class<?> loadedClass;
        try {
            loadedClass = Loader.loadClass(String.class.getName());
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }

        assertThat(loadedClass).isEqualTo(String.class);
        assertThat(classLoader.getDelegatedLoads()).contains(String.class.getName());
    }

    @Test
    void fallsBackToClassForNameWhenThreadContextClassLoaderCannotLoadClass() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        RejectingClassLoader classLoader = new RejectingClassLoader(
                LoaderTest.class.getClassLoader(),
                Set.of(String.class.getName())
        );
        thread.setContextClassLoader(classLoader);

        Class<?> loadedClass;
        try {
            loadedClass = Loader.loadClass(String.class.getName());
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }

        assertThat(loadedClass).isEqualTo(String.class);
        assertThat(classLoader.getRejectedLoads()).contains(String.class.getName());
    }

    @Test
    void canIgnoreThreadContextClassLoaderWhenConfiguredBeforeLoaderInitialization() throws Exception {
        String previousValue = System.getProperty(IGNORE_TCL_PROPERTY_NAME);
        System.setProperty(IGNORE_TCL_PROPERTY_NAME, Boolean.TRUE.toString());

        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(new URL[] {
                codeSourceUrl(LoaderTest.class),
                resourceRootUrl(CALLABLE_SERVICE_RESOURCE),
                codeSourceUrl(Loader.class)
        }, LoaderTest.class.getClassLoader())) {
            Callable<?> action = ServiceLoader.load(Callable.class, classLoader).stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(provider -> provider.getClass().getName().equals(ISOLATED_ACTION_CLASS_NAME))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected an isolated Loader callable provider"));

            assertThat(action.call()).isEqualTo(String.class.getName());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            restoreSystemProperty(previousValue);
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();

        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static URL resourceRootUrl(String resourceName) throws IOException {
        URL resourceUrl = LoaderTest.class.getClassLoader().getResource(resourceName);

        assertThat(resourceUrl).isNotNull();
        if ("jar".equals(resourceUrl.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) resourceUrl.openConnection();
            return connection.getJarFileURL();
        }
        String externalForm = resourceUrl.toExternalForm();
        assertThat(externalForm).endsWith(resourceName);
        return new URL(externalForm.substring(0, externalForm.length() - resourceName.length()));
    }

    private static void restoreSystemProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(IGNORE_TCL_PROPERTY_NAME);
        } else {
            System.setProperty(IGNORE_TCL_PROPERTY_NAME, previousValue);
        }
    }

    private static final class TrackingResourceClassLoader extends ClassLoader {

        private final Set<String> requestedResources = new LinkedHashSet<>();
        private final String trackedResource;

        private TrackingResourceClassLoader(ClassLoader parent, String trackedResource) {
            super(parent);
            this.trackedResource = trackedResource;
        }

        @Override
        public URL getResource(String name) {
            if (trackedResource.equals(name)) {
                requestedResources.add(name);
            }
            return super.getResource(name);
        }

        private Set<String> getRequestedResources() {
            return requestedResources;
        }
    }

    private static final class RejectingResourceClassLoader extends ClassLoader {

        private final Set<String> rejectedResources = new LinkedHashSet<>();
        private final Set<String> rejectedResourceNames;

        private RejectingResourceClassLoader(ClassLoader parent, Set<String> rejectedResourceNames) {
            super(parent);
            this.rejectedResourceNames = rejectedResourceNames;
        }

        @Override
        public URL getResource(String name) {
            if (rejectedResourceNames.contains(name)) {
                rejectedResources.add(name);
                return null;
            }
            return super.getResource(name);
        }

        private Set<String> getRejectedResources() {
            return rejectedResources;
        }
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
            return className.startsWith(LOG4J1_PACKAGE) || className.equals(ISOLATED_ACTION_CLASS_NAME);
        }
    }
}
