/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.aspectj.weaver.tools.TraceFactory;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceFactoryTest {
    private static final String FACTORY_PROPERTY = "org.aspectj.tracing.factory";
    private static final String TRACE_FACTORY_CLASS_NAME = "org.aspectj.weaver.tools.TraceFactory";
    private static final String JDK14_TRACE_FACTORY_CLASS_NAME = "org.aspectj.weaver.tools.Jdk14TraceFactory";

    @Test
    void staticInitializerInstantiatesConfiguredTraceFactory() throws Exception {
        Object factory = null;
        boolean completed = false;
        try {
            factory = loadTraceFactoryWithConfiguredFactoryProperty();
            completed = true;
        } catch (InvocationTargetException exception) {
            rethrowIfNotNativeImageDynamicClassLoadingError(exception);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }

        if (completed) {
            assertThat(factory.getClass().getName()).isEqualTo(JDK14_TRACE_FACTORY_CLASS_NAME);
        }
    }

    private static Object loadTraceFactoryWithConfiguredFactoryProperty() throws Exception {
        String previousFactoryProperty = System.getProperty(FACTORY_PROPERTY);
        System.setProperty(FACTORY_PROPERTY, JDK14_TRACE_FACTORY_CLASS_NAME);
        try (ChildFirstAspectjClassLoader classLoader = new ChildFirstAspectjClassLoader(aspectjWeaverLocation())) {
            Class<?> traceFactoryClass = classLoader.loadClass(TRACE_FACTORY_CLASS_NAME);
            Method getTraceFactory = traceFactoryClass.getMethod("getTraceFactory");
            return getTraceFactory.invoke(null);
        } finally {
            restoreFactoryProperty(previousFactoryProperty);
        }
    }

    private static URL aspectjWeaverLocation() {
        return TraceFactory.class.getProtectionDomain().getCodeSource().getLocation();
    }

    private static void restoreFactoryProperty(String previousFactoryProperty) {
        if (previousFactoryProperty == null) {
            System.clearProperty(FACTORY_PROPERTY);
        } else {
            System.setProperty(FACTORY_PROPERTY, previousFactoryProperty);
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(InvocationTargetException exception)
            throws InvocationTargetException {
        if (!hasUnsupportedFeatureError(exception)) {
            throw exception;
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!hasUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class ChildFirstAspectjClassLoader extends URLClassLoader {
        private ChildFirstAspectjClassLoader(URL aspectjWeaverLocation) {
            super(new URL[] {aspectjWeaverLocation}, TraceFactoryTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && name.startsWith("org.aspectj.")) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = null;
                    }
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }
}
