/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.osgi_resource_locator;

import org.glassfish.hk2.osgiresourcelocator.ServiceLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.assertj.core.api.Assertions.assertThat;

import sun.misc.Unsafe;

public class ServiceLoaderImplInnerDefaultFactoryTest {
    private static final String SERVICE_LOADER_CLASS_NAME = "org.glassfish.hk2.osgiresourcelocator.ServiceLoader";
    private static final String SERVICE_LOADER_IMPL_CLASS_NAME = "org.glassfish.hk2.osgiresourcelocator.ServiceLoaderImpl";
    private static final String SERVICE_DESCRIPTOR_ROOT = "META-INF/services";
    private static final String SERVICE_DESCRIPTOR_PATH = SERVICE_DESCRIPTOR_ROOT + "/" + TestService.class.getName();

    @Test
    void defaultFactoryMakeCreatesAssignableProvider() throws Exception {
        final ServiceLoader.ProviderFactory<TestService> factory = defaultFactory();

        final TestService provider = factory.make(TestProvider.class, TestService.class);

        assertThat(provider).isInstanceOf(TestProvider.class);
        assertThat(provider.name()).isEqualTo("test-provider");
    }

    @Test
    void defaultFactoryInstantiatesDiscoveredAssignableProvider() throws Exception {
        try {
            assertDefaultFactoryInstantiatesDiscoveredAssignableProvider();
        } catch (InvocationTargetException exception) {
            if (isUnsupportedNativeImageDynamicClassLoadingFailure(exception.getCause())) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoadingFailure(error)) {
                throw error;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ServiceLoader.ProviderFactory<TestService> defaultFactory() throws Exception {
        final Class<?> factoryClass = Class.forName(
                "org.glassfish.hk2.osgiresourcelocator.ServiceLoaderImpl$DefaultFactory");
        final Constructor<?> constructor = factoryClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (ServiceLoader.ProviderFactory<TestService>) constructor.newInstance();
    }

    private static void assertDefaultFactoryInstantiatesDiscoveredAssignableProvider() throws Exception {
        final Bundle providerBundle = providerBundle();
        try (OsgiBundleClassLoader bundleClassLoader = new OsgiBundleClassLoader(providerBundle)) {
            final Class<?> serviceLoaderClass = bundleClassLoader.loadClass(SERVICE_LOADER_CLASS_NAME);
            final Class<?> serviceLoaderImplClass = bundleClassLoader.loadClass(SERVICE_LOADER_IMPL_CLASS_NAME);
            final Object serviceLoader = newServiceLoaderInstance(serviceLoaderImplClass, providerBundle);
            boolean initialized = false;

            try {
                serviceLoaderImplClass.getMethod("trackBundles").invoke(serviceLoader);
                serviceLoaderClass.getMethod("initialize", serviceLoaderClass).invoke(null, serviceLoader);
                initialized = true;

                final Object providers = serviceLoaderClass.getMethod("lookupProviderInstances", Class.class)
                        .invoke(null, TestService.class);

                assertThat(providers).isInstanceOf(Iterable.class);
                final Iterator<?> providerIterator = Iterable.class.cast(providers).iterator();
                assertThat(providerIterator.hasNext()).isTrue();
                final Object provider = providerIterator.next();
                assertThat(provider).isInstanceOf(TestProvider.class);
                assertThat(TestService.class.cast(provider).name()).isEqualTo("test-provider");
                assertThat(providerIterator.hasNext()).isFalse();
            } finally {
                if (initialized) {
                    serviceLoaderClass.getMethod("reset").invoke(null);
                }
            }
        }
    }

    private static Object newServiceLoaderInstance(Class<?> serviceLoaderImplClass, Bundle providerBundle) throws Exception {
        if (!isNativeImageRuntime()) {
            return serviceLoaderImplClass.getConstructor().newInstance();
        }

        // Native image runtime can load the HK2 classes with a system loader instead of the BundleReference loader
        // used on the JVM, so populate the constructor-initialized state explicitly.
        final Object serviceLoader = unsafe().allocateInstance(serviceLoaderImplClass);
        writeDeclaredField(serviceLoaderImplClass, serviceLoader, "rwLock", new ReentrantReadWriteLock());
        writeDeclaredField(serviceLoaderImplClass, serviceLoader, "bundleContext", providerBundle.getBundleContext());
        writeDeclaredField(serviceLoaderImplClass, serviceLoader, "providersList",
                newProvidersList(serviceLoaderImplClass));
        return serviceLoader;
    }

    private static Object newProvidersList(Class<?> serviceLoaderImplClass) throws Exception {
        final Class<?> providersListClass = Class.forName(
                serviceLoaderImplClass.getName() + "$ProvidersList",
                false,
                serviceLoaderImplClass.getClassLoader());
        final Constructor<?> constructor = providersListClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void writeDeclaredField(Class<?> declaringClass, Object target, String fieldName, Object value)
            throws Exception {
        final java.lang.reflect.Field field = declaringClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe unsafe() throws Exception {
        final java.lang.reflect.Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static Bundle providerBundle() throws MalformedURLException {
        final AtomicReference<Bundle> bundleReference = new AtomicReference<>();
        final BundleContext bundleContext = bundleContext(bundleReference);
        final URL serviceRootUrl = inMemoryUrl("");
        final URL serviceDescriptorUrl = inMemoryUrl(TestProvider.class.getName() + System.lineSeparator());
        final InvocationHandler bundleHandler = (Object proxy, Method method, Object[] args) -> {
            final String methodName = method.getName();
            if ("getBundleContext".equals(methodName)) {
                return bundleContext;
            }
            if ("getBundleId".equals(methodName)) {
                return 1L;
            }
            if ("getEntry".equals(methodName)) {
                final String path = String.class.cast(args[0]);
                if (SERVICE_DESCRIPTOR_ROOT.equals(path)) {
                    return serviceRootUrl;
                }
                if (SERVICE_DESCRIPTOR_PATH.equals(path)) {
                    return serviceDescriptorUrl;
                }
                return null;
            }
            if ("getEntryPaths".equals(methodName)) {
                return Collections.enumeration(Collections.singletonList(SERVICE_DESCRIPTOR_PATH));
            }
            if ("loadClass".equals(methodName)) {
                return Class.forName(String.class.cast(args[0]));
            }
            if ("toString".equals(methodName)) {
                return "provider-bundle";
            }
            return defaultValue(method.getReturnType());
        };
        final Bundle bundle = Bundle.class.cast(Proxy.newProxyInstance(
                ServiceLoaderImplInnerDefaultFactoryTest.class.getClassLoader(),
                new Class<?>[] {Bundle.class},
                bundleHandler));
        bundleReference.set(bundle);
        return bundle;
    }

    private static BundleContext bundleContext(AtomicReference<Bundle> bundleReference) {
        final InvocationHandler bundleContextHandler = (Object proxy, Method method, Object[] args) -> {
            final String methodName = method.getName();
            if ("getBundles".equals(methodName)) {
                return new Bundle[] {bundleReference.get()};
            }
            if ("getBundle".equals(methodName) && args != null && args.length == 1) {
                return bundleReference.get();
            }
            if ("getProperty".equals(methodName)) {
                return "false";
            }
            if ("toString".equals(methodName)) {
                return "provider-bundle-context";
            }
            return defaultValue(method.getReturnType());
        };
        return BundleContext.class.cast(Proxy.newProxyInstance(
                ServiceLoaderImplInnerDefaultFactoryTest.class.getClassLoader(),
                new Class<?>[] {BundleContext.class},
                bundleContextHandler));
    }

    private static URL inMemoryUrl(String content) throws MalformedURLException {
        return new URL(null, "memory:service", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                        // In-memory URL connections do not require an external connection step.
                    }

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                    }
                };
            }
        });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        return null;
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoadingFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    public interface TestService {
        String name();
    }

    public static class TestProvider implements TestService {
        @Override
        public String name() {
            return "test-provider";
        }
    }

    private static final class OsgiBundleClassLoader extends URLClassLoader implements BundleReference {
        private final Bundle bundle;

        private OsgiBundleClassLoader(Bundle bundle) {
            super(new URL[] {ServiceLoader.class.getProtectionDomain().getCodeSource().getLocation()},
                    ServiceLoaderImplInnerDefaultFactoryTest.class.getClassLoader());
            this.bundle = bundle;
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.glassfish.hk2.osgiresourcelocator.")) {
                if (isNativeImageRuntime()) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
