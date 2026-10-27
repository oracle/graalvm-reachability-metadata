/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.osgi_resource_locator;

import org.glassfish.hk2.osgiresourcelocator.ServiceLoader;
import org.glassfish.hk2.osgiresourcelocator.ServiceLoaderImpl;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceLoaderImplInnerDefaultFactoryTest {

    private static final String SERVICES_DIRECTORY = "META-INF/services";
    private static final String SERVICE_PATH = SERVICES_DIRECTORY
            + "/" + DefaultFactoryService.class.getName();

    @Test
    void createsProviderInstancesFromTrackedBundleServiceEntries() throws Exception {
        final Bundle providerBundle = providerBundle();
        final BundleContext bundleContext = bundleContext(providerBundle);
        final ServiceLoaderImpl serviceLoader = serviceLoaderWithBundleContext(bundleContext);
        boolean initialized = false;
        try {
            serviceLoader.trackBundles();
            ServiceLoader.initialize(serviceLoader);
            initialized = true;

            final Iterable<? extends DefaultFactoryService> providerInstances =
                    ServiceLoader.lookupProviderInstances(DefaultFactoryService.class);
            final List<DefaultFactoryService> instances = iterableToList(providerInstances);
            assertThat(instances).hasSize(1);
            assertThat(instances.get(0)).isInstanceOf(DefaultFactoryProvider.class);
        } finally {
            if (initialized) {
                ServiceLoader.reset();
            }
        }
    }

    private static ServiceLoaderImpl serviceLoaderWithBundleContext(BundleContext bundleContext)
            throws Exception {
        final ServiceLoaderImpl serviceLoader = allocateServiceLoader();
        setField(serviceLoader, "rwLock", new ReentrantReadWriteLock());
        setField(serviceLoader, "bundleContext", bundleContext);
        setField(serviceLoader, "providersList", newProvidersList());
        return serviceLoader;
    }

    private static ServiceLoaderImpl allocateServiceLoader() throws Exception {
        return ServiceLoaderImpl.class.cast(unsafe().allocateInstance(ServiceLoaderImpl.class));
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        final Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return Unsafe.class.cast(field.get(null));
    }

    private static Object newProvidersList() throws ReflectiveOperationException {
        final Class<?> type = Class.forName(ServiceLoaderImpl.class.getName() + "$ProvidersList");
        final Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static List<DefaultFactoryService> iterableToList(
            Iterable<? extends DefaultFactoryService> iterable) {
        final List<DefaultFactoryService> values = new ArrayList<>();
        iterable.forEach(values::add);
        return values;
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
                DefaultFactoryProvider.class.getName() + System.lineSeparator());
        return proxy(Bundle.class, (proxy, method, arguments) -> {
            final String methodName = method.getName();
            if ("getBundleId".equals(methodName)) {
                return 11L;
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
                    && DefaultFactoryProvider.class.getName().equals(arguments[0])) {
                return DefaultFactoryProvider.class;
            }
            return defaultValue(proxy, method, arguments);
        });
    }

    private static URL serviceDescriptorUrl(String content) throws IOException {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new URL(null, "memory:default-factory-service-descriptor", new URLStreamHandler() {
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

    public interface DefaultFactoryService {
    }

    public static class DefaultFactoryProvider implements DefaultFactoryService {
    }
}
