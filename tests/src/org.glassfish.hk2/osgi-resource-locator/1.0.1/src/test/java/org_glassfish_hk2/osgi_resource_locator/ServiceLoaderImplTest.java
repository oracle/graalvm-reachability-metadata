/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.osgi_resource_locator;

import org.glassfish.hk2.osgiresourcelocator.ServiceLoader;
import org.glassfish.hk2.osgiresourcelocator.ServiceLoaderImpl;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceLoaderImplTest {

    private static final String SERVICES_DIRECTORY = "META-INF/services";
    private static final String SERVICE_PATH = SERVICES_DIRECTORY
            + "/" + TestService.class.getName();

    @Test
    void discoversProviderClassesFromTrackedBundles() throws Exception {
        try {
            assertProviderClassesAreDiscovered();
        } catch (InvocationTargetException exception) {
            if (!isUnsupportedFeatureError(exception.getCause())) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static boolean isUnsupportedFeatureError(Throwable throwable) {
        return throwable instanceof Error error
                && NativeImageSupport.isUnsupportedFeatureError(error);
    }

    private static void assertProviderClassesAreDiscovered() throws Exception {
        final Bundle providerBundle = providerBundle();
        final BundleContext bundleContext = bundleContext(providerBundle);
        final Bundle locatorBundle = locatorBundle(bundleContext);
        final BundleBackedClassLoader classLoader = new BundleBackedClassLoader(locatorBundle);
        final Class<?> serviceLoaderClass = Class.forName(
                ServiceLoader.class.getName(), true, classLoader);
        final Class<?> implementationClass = Class.forName(
                ServiceLoaderImpl.class.getName(), true, classLoader);
        final Object serviceLoader = implementationClass.getDeclaredConstructor().newInstance();
        boolean initialized = false;
        try {
            implementationClass.getMethod("trackBundles").invoke(serviceLoader);
            serviceLoaderClass.getMethod("initialize", serviceLoaderClass)
                    .invoke(null, serviceLoader);
            initialized = true;

            final Iterable<?> providerClasses = (Iterable<?>) serviceLoaderClass
                    .getMethod("lookupProviderClasses", Class.class)
                    .invoke(null, TestService.class);
            assertThat(iterableToList(providerClasses)).containsExactly(TestProvider.class);
        } finally {
            if (initialized) {
                serviceLoaderClass.getMethod("reset").invoke(null);
            }
        }
    }

    private static List<Object> iterableToList(Iterable<?> iterable) {
        final List<Object> values = new ArrayList<>();
        iterable.forEach(values::add);
        return values;
    }

    private static Bundle locatorBundle(BundleContext bundleContext) {
        return proxy(Bundle.class, (proxy, method, arguments) -> {
            if ("getBundleContext".equals(method.getName())) {
                return bundleContext;
            }
            return defaultValue(proxy, method, arguments);
        });
    }

    private static BundleContext bundleContext(Bundle providerBundle) {
        return proxy(BundleContext.class, (proxy, method, arguments) -> {
            final String methodName = method.getName();
            if ("addBundleListener".equals(methodName)) {
                return null;
            }
            if ("getBundles".equals(methodName)) {
                return new Bundle[] {providerBundle};
            }
            if ("getBundle".equals(methodName) && arguments != null && arguments.length == 1) {
                final Long bundleId = Long.class.cast(arguments[0]);
                return bundleId == providerBundle.getBundleId() ? providerBundle : null;
            }
            if ("getProperty".equals(methodName)) {
                return null;
            }
            return defaultValue(proxy, method, arguments);
        });
    }

    private static Bundle providerBundle() throws IOException {
        final URL serviceDescriptor = serviceDescriptorUrl(
                TestProvider.class.getName() + System.lineSeparator());
        return proxy(Bundle.class, (proxy, method, arguments) -> {
            final String methodName = method.getName();
            if ("getBundleId".equals(methodName)) {
                return 7L;
            }
            if ("getEntry".equals(methodName)) {
                final String path = String.class.cast(arguments[0]);
                if (SERVICES_DIRECTORY.equals(path) || SERVICE_PATH.equals(path)) {
                    return serviceDescriptor;
                }
                return null;
            }
            if ("getEntryPaths".equals(methodName) && SERVICES_DIRECTORY.equals(arguments[0])) {
                return Collections.enumeration(Collections.singleton(SERVICE_PATH));
            }
            if ("loadClass".equals(methodName)
                    && TestProvider.class.getName().equals(arguments[0])) {
                return TestProvider.class;
            }
            return defaultValue(proxy, method, arguments);
        });
    }

    private static URL serviceDescriptorUrl(String content) throws IOException {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new URL(null, "memory:service-descriptor", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                    }

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(bytes);
                    }
                };
            }
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object defaultValue(Object proxy, Method method, Object[] arguments) {
        if (method.getDeclaringClass() == Object.class) {
            return objectMethodValue(proxy, method, arguments);
        }
        final Class<?> returnType = method.getReturnType();
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0.0F;
        }
        if (returnType == Double.TYPE) {
            return 0.0D;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        return null;
    }

    private static Object objectMethodValue(Object proxy, Method method, Object[] arguments) {
        final String methodName = method.getName();
        if ("toString".equals(methodName)) {
            return proxy.getClass().getInterfaces()[0].getSimpleName() + " proxy";
        }
        if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(methodName)) {
            return proxy == arguments[0];
        }
        return null;
    }

    public interface TestService {
    }

    public static class TestProvider implements TestService {
    }

    private static final class BundleBackedClassLoader extends ClassLoader
            implements BundleReference {
        private final Bundle bundle;

        BundleBackedClassLoader(Bundle bundle) throws IOException, ClassNotFoundException {
            super(ServiceLoaderImpl.class.getClassLoader());
            this.bundle = bundle;
            defineServiceLoaderClass(ServiceLoader.class.getName());
            defineServiceLoaderClass(ServiceLoaderImpl.class.getName());
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (isServiceLoaderImplementationClass(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = findClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return defineServiceLoaderClass(name);
            } catch (IOException exception) {
                final ClassNotFoundException classNotFoundException =
                        new ClassNotFoundException(name);
                classNotFoundException.initCause(exception);
                throw classNotFoundException;
            }
        }

        private Class<?> defineServiceLoaderClass(String name) throws IOException,
                ClassNotFoundException {
            final Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) {
                return loadedClass;
            }
            final String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                final byte[] bytes = inputStream.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            }
        }

        private static boolean isServiceLoaderImplementationClass(String name) {
            return ServiceLoader.class.getName().equals(name)
                    || ServiceLoaderImpl.class.getName().equals(name)
                    || name.startsWith(ServiceLoader.class.getName() + "$")
                    || name.startsWith(ServiceLoaderImpl.class.getName() + "$");
        }
    }
}
