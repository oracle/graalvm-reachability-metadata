/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.util.Loader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderTest {
    private static final String LOADABLE_CLASS_NAME = ContextBase.class.getName();
    private static final String MISSING_RESOURCE = "ch_qos_logback/logback_core/missing-loader-resource.properties";
    private static final String CHILD_FIRST_INVOKER_NAME = LoaderTest.ChildFirstLoaderInvoker.class.getName();

    @Test
    void getResourcesReturnsEmptySetForMissingClasspathResource() throws IOException {
        Set<URL> resources = Loader.getResources(MISSING_RESOURCE, LoaderTest.class.getClassLoader());

        assertThat(resources).isEmpty();
    }

    @Test
    void getResourceReturnsNullForMissingClasspathResource() {
        URL resource = Loader.getResource(MISSING_RESOURCE, LoaderTest.class.getClassLoader());

        assertThat(resource).isNull();
    }

    @Test
    void loadClassWithContextUsesContextClassLoader() throws ClassNotFoundException {
        ContextBase context = new ContextBase();

        Class<?> loadedClass = Loader.loadClass(LOADABLE_CLASS_NAME, context);

        assertThat(loadedClass).isSameAs(ContextBase.class);
    }

    @Test
    void loadClassUsesThreadContextClassLoader() throws ClassNotFoundException {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(LoaderTest.class.getClassLoader());
        try {
            Class<?> loadedClass = Loader.loadClass(LOADABLE_CLASS_NAME);

            assertThat(loadedClass).isSameAs(ContextBase.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToClassForNameWhenThreadContextClassLoaderFails() throws ClassNotFoundException {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new FailingClassLoader(previousClassLoader, LOADABLE_CLASS_NAME));
        try {
            Class<?> loadedClass = Loader.loadClass(LOADABLE_CLASS_NAME);

            assertThat(loadedClass).isSameAs(ContextBase.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void loadClassHonorsIgnoreThreadContextLoaderPropertyInFreshClassLoader() throws Exception {
        try {
            String previousValue = System.getProperty(Loader.IGNORE_TCL_PROPERTY_NAME);
            System.setProperty(Loader.IGNORE_TCL_PROPERTY_NAME, "true");
            try (ChildFirstLogbackClassLoader classLoader = new ChildFirstLogbackClassLoader(childFirstUrls())) {
                Callable<?> invoker = ServiceLoader.load(Callable.class, classLoader).stream()
                        .filter(provider -> CHILD_FIRST_INVOKER_NAME.equals(provider.type().getName()))
                        .findFirst()
                        .orElseThrow()
                        .get();

                Object loadedClass = invoker.call();

                assertThat(loadedClass).isInstanceOf(Class.class);
                assertThat(((Class<?>) loadedClass).getName()).isEqualTo(LOADABLE_CLASS_NAME);
            } finally {
                restoreProperty(Loader.IGNORE_TCL_PROPERTY_NAME, previousValue);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static URL[] childFirstUrls() {
        URL testClassesUrl = LoaderTest.class.getProtectionDomain().getCodeSource().getLocation();
        URL logbackCoreUrl = Loader.class.getProtectionDomain().getCodeSource().getLocation();
        return new URL[] { testClassesUrl, logbackCoreUrl };
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    public static final class ChildFirstLoaderInvoker implements Callable<Class<?>> {
        @Override
        public Class<?> call() throws ClassNotFoundException {
            return Loader.loadClass(ContextBase.class.getName());
        }
    }

    private static final class FailingClassLoader extends ClassLoader {
        private final String failingClassName;

        private FailingClassLoader(ClassLoader parent, String failingClassName) {
            super(parent);
            this.failingClassName = failingClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (failingClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }

    private static final class ChildFirstLogbackClassLoader extends URLClassLoader {
        private static final List<String> CHILD_FIRST_CLASS_NAMES = List.of(
                LoaderTest.class.getName(),
                CHILD_FIRST_INVOKER_NAME
        );
        private static final List<String> CHILD_FIRST_PACKAGE_PREFIXES = List.of("ch.qos.logback.");

        private ChildFirstLogbackClassLoader(URL[] urls) {
            super(urls, LoaderTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = loadClassByPolicy(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> loadClassByPolicy(String name) throws ClassNotFoundException {
            if (isChildFirst(name)) {
                try {
                    return findClass(name);
                } catch (ClassNotFoundException ignored) {
                    return super.loadClass(name, false);
                }
            }
            return super.loadClass(name, false);
        }

        private boolean isChildFirst(String name) {
            return CHILD_FIRST_CLASS_NAMES.contains(name)
                    || CHILD_FIRST_PACKAGE_PREFIXES.stream().anyMatch(name::startsWith);
        }
    }
}
