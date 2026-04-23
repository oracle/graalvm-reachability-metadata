/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

public class DatatypeLibraryLoaderServiceLoader2DynamicAccessTest {
    private static final String LOADER2_CLASS_NAME =
            "com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader$Service$Loader2";
    private static final String LOADER2_CLASS_CACHE_FIELD =
            "class$org$relaxng$datatype$helpers$DatatypeLibraryLoader$Service$Loader2";
    private static final String SERVICE_RESOURCE =
            "META-INF/services/com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibraryFactory";
    private static final String PROVIDER_CLASS_NAME =
            DatatypeLibraryLoaderServiceDynamicAccessTest.TestDatatypeLibraryFactory.class.getName();

    @Test
    void coversLegacyContextLoaderResourceAndClassResolutionPaths() throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        TrackingContextClassLoader contextLoader =
                new TrackingContextClassLoader(DatatypeLibraryLoaderServiceLoader2DynamicAccessTest.class.getClassLoader());
        Class<?> loader2Class = Class.forName(LOADER2_CLASS_NAME);
        Field cacheField = accessibleField(loader2Class, LOADER2_CLASS_CACHE_FIELD);
        Object previousCache = cacheField.get(null);

        try {
            Thread.currentThread().setContextClassLoader(contextLoader);
            cacheField.set(null, null);
            Object loader2 = newInstance(loader2Class);

            Enumeration<?> resources = invokeResources(loader2Class, loader2, SERVICE_RESOURCE);
            assertThat(resources.hasMoreElements()).isTrue();
            assertThat(((URL) resources.nextElement()).toExternalForm()).contains("META-INF/services");

            Class<?> providerClass = invokeLoadClass(loader2Class, loader2, PROVIDER_CLASS_NAME);
            assertThat(providerClass)
                    .isEqualTo(DatatypeLibraryLoaderServiceDynamicAccessTest.TestDatatypeLibraryFactory.class);
            assertThat(contextLoader.requestedServiceResource()).isEqualTo(SERVICE_RESOURCE);
            assertThat(contextLoader.loadedProviderClassName()).isEqualTo(PROVIDER_CLASS_NAME);
        } finally {
            cacheField.set(null, previousCache);
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static Field accessibleField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object newInstance(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Enumeration<?> invokeResources(Class<?> loaderClass, Object loader, String resourceName) throws Exception {
        Method method = loaderClass.getDeclaredMethod("getResources", String.class);
        method.setAccessible(true);
        return (Enumeration<?>) method.invoke(loader, resourceName);
    }

    private static Class<?> invokeLoadClass(Class<?> loaderClass, Object loader, String className) throws Exception {
        Method method = loaderClass.getDeclaredMethod("loadClass", String.class);
        method.setAccessible(true);
        return (Class<?>) method.invoke(loader, className);
    }

    private static final class TrackingContextClassLoader extends ClassLoader {
        private String requestedServiceResource;
        private String loadedProviderClassName;

        private TrackingContextClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SERVICE_RESOURCE.equals(name)) {
                requestedServiceResource = name;
            }
            return super.getResources(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (PROVIDER_CLASS_NAME.equals(name)) {
                loadedProviderClassName = name;
            }
            return super.loadClass(name, resolve);
        }

        private String requestedServiceResource() {
            return requestedServiceResource;
        }

        private String loadedProviderClassName() {
            return loadedProviderClassName;
        }
    }
}
