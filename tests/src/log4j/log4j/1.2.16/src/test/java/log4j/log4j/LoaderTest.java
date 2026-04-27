/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import org.apache.log4j.helpers.Loader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderTest {

    private static final String TEST_CLASS_RESOURCE = LoaderTest.class.getName().replace('.', '/') + ".class";
    private static final String IGNORE_TCL_PROPERTY_NAME = "log4j.ignoreTCL";

    @Test
    void loadsResourceFromThreadContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(LoaderTest.class.getClassLoader());

        try {
            URL resource = Loader.getResource(TEST_CLASS_RESOURCE);

            assertThat(resource).isNotNull();
            assertThat(resource.toExternalForm()).contains(TEST_CLASS_RESOURCE);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void fallsBackToLoaderClassLoaderWhenThreadContextClassLoaderCannotFindResource() {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new MissingResourceClassLoader(LoaderTest.class.getClassLoader()));

        try {
            URL resource = Loader.getResource(TEST_CLASS_RESOURCE);

            assertThat(resource).isNotNull();
            assertThat(resource.toExternalForm()).contains(TEST_CLASS_RESOURCE);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void fallsBackToSystemResourceWhenIsolatedLoaderCannotFindResource() throws Exception {
        URL resource = invokeIsolatedLoaderGetResource(TEST_CLASS_RESOURCE, new MissingResourceClassLoader(LoaderTest.class.getClassLoader()));

        assertThat(resource).isNotNull();
        assertThat(resource.toExternalForm()).contains(TEST_CLASS_RESOURCE);
    }

    @Test
    void loadsClassThroughThreadContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(LoaderTest.class.getClassLoader());

        try {
            Class<?> loadedClass = Loader.loadClass(LoaderTest.class.getName());

            assertThat(loadedClass).isEqualTo(LoaderTest.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void fallsBackToClassForNameWhenThreadContextClassLoaderCannotLoadClass() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new FailingClassLoader(LoaderTest.class.getClassLoader(), LoaderTest.class.getName()));

        try {
            Class<?> loadedClass = Loader.loadClass(LoaderTest.class.getName());

            assertThat(loadedClass).isEqualTo(LoaderTest.class);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void usesClassForNameWhenThreadContextClassLoaderIsIgnored() throws Exception {
        String previousProperty = System.getProperty(IGNORE_TCL_PROPERTY_NAME);
        System.setProperty(IGNORE_TCL_PROPERTY_NAME, Boolean.TRUE.toString());

        try {
            Class<?> loadedClass = invokeIsolatedLoaderLoadClass(String.class.getName(), new FailingClassLoader(LoaderTest.class.getClassLoader(), String.class.getName()));

            assertThat(loadedClass).isEqualTo(String.class);
        } finally {
            restoreSystemProperty(previousProperty);
        }
    }

    private static URL invokeIsolatedLoaderGetResource(String resourceName, ClassLoader contextClassLoader) throws Exception {
        try (URLClassLoader isolatedLoader = new URLClassLoader(new URL[]{codeSourceUrl(Loader.class)}, ClassLoader.getPlatformClassLoader())) {
            Thread thread = Thread.currentThread();
            ClassLoader previousClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(contextClassLoader);

            try {
                Class<?> isolatedLoaderClass = Class.forName(Loader.class.getName(), true, isolatedLoader);
                Method getResourceMethod = isolatedLoaderClass.getMethod("getResource", String.class);
                return (URL) getResourceMethod.invoke(null, resourceName);
            } finally {
                thread.setContextClassLoader(previousClassLoader);
            }
        }
    }

    private static Class<?> invokeIsolatedLoaderLoadClass(String className, ClassLoader contextClassLoader) throws Exception {
        try (URLClassLoader isolatedLoader = new URLClassLoader(new URL[]{codeSourceUrl(Loader.class)}, ClassLoader.getPlatformClassLoader())) {
            Thread thread = Thread.currentThread();
            ClassLoader previousClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(contextClassLoader);

            try {
                Class<?> isolatedLoaderClass = Class.forName(Loader.class.getName(), true, isolatedLoader);
                Method loadClassMethod = isolatedLoaderClass.getMethod("loadClass", String.class);
                return (Class<?>) loadClassMethod.invoke(null, className);
            } finally {
                thread.setContextClassLoader(previousClassLoader);
            }
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

    private static final class MissingResourceClassLoader extends ClassLoader {

        private MissingResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            return null;
        }
    }

    private static final class FailingClassLoader extends ClassLoader {

        private final String missingClassName;

        private FailingClassLoader(ClassLoader parent, String missingClassName) {
            super(parent);
            this.missingClassName = missingClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (missingClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
