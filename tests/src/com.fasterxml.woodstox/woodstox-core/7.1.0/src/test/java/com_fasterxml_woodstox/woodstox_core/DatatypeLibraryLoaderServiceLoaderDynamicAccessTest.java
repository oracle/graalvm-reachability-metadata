/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibraryFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

public class DatatypeLibraryLoaderServiceLoaderDynamicAccessTest {
    private static final String LOADER_CLASS_NAME =
            "com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader$Service$Loader";
    private static final String SERVICE_CLASS_NAME =
            "com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader$Service";
    private static final String LOADER_CLASS_CACHE_FIELD =
            "class$org$relaxng$datatype$helpers$DatatypeLibraryLoader$Service$Loader";
    private static final String SERVICE_RESOURCE =
            "META-INF/services/" + DatatypeLibraryFactory.class.getName();

    @Test
    void coversLegacyLoaderClassAndResourceResolutionPaths() throws Exception {
        Class<?> loaderClass = Class.forName(LOADER_CLASS_NAME);
        Class<?> serviceClass = Class.forName(SERVICE_CLASS_NAME);
        Object loader = newInstance(loaderClass, new Class<?>[0], new Object[0]);
        Object service = newInstance(serviceClass, new Class<?>[]{Class.class}, new Object[]{DatatypeLibraryFactory.class});
        Field cacheField = accessibleField(loaderClass, LOADER_CLASS_CACHE_FIELD);
        Field loaderField = accessibleField(serviceClass, "loader");
        Object previousValue = cacheField.get(null);
        loaderField.set(service, loader);

        try {
            cacheField.set(null, null);
            Enumeration<?> classLoaderResources = invokeResources(loaderClass, loader);
            assertThat(classLoaderResources.hasMoreElements()).isTrue();
            URL classLoaderResource = (URL) classLoaderResources.nextElement();
            assertThat(classLoaderResource.toExternalForm()).contains("META-INF/services");

            Enumeration<?> providers = invokeProviders(serviceClass, service);
            assertThat(providers.hasMoreElements()).isTrue();
            assertThat(providers.nextElement())
                    .isInstanceOf(DatatypeLibraryLoaderServiceDynamicAccessTest.TestDatatypeLibraryFactory.class);

            Class<?> factoryClass = invokeLoadClass(
                    loaderClass,
                    loader,
                    DatatypeLibraryLoaderServiceDynamicAccessTest.TestDatatypeLibraryFactory.class.getName());
            assertThat(factoryClass)
                    .isEqualTo(DatatypeLibraryLoaderServiceDynamicAccessTest.TestDatatypeLibraryFactory.class);

            cacheField.set(null, String.class);
            Enumeration<?> systemResources = invokeResources(loaderClass, loader);
            assertThat(systemResources.hasMoreElements()).isTrue();
            URL systemResource = (URL) systemResources.nextElement();
            assertThat(systemResource.toExternalForm()).contains("META-INF/services");
        } finally {
            cacheField.set(null, previousValue);
        }
    }

    private static Field accessibleField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object newInstance(Class<?> type, Class<?>[] parameterTypes, Object[] arguments) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static Enumeration<?> invokeProviders(Class<?> serviceClass, Object service) throws Exception {
        Method method = serviceClass.getDeclaredMethod("getProviders");
        method.setAccessible(true);
        return (Enumeration<?>) method.invoke(service);
    }

    private static Enumeration<?> invokeResources(Class<?> loaderClass, Object loader) throws Exception {
        Method method = loaderClass.getDeclaredMethod("getResources", String.class);
        method.setAccessible(true);
        return (Enumeration<?>) method.invoke(loader, SERVICE_RESOURCE);
    }

    private static Class<?> invokeLoadClass(Class<?> loaderClass, Object loader, String className) throws Exception {
        Method method = loaderClass.getDeclaredMethod("loadClass", String.class);
        method.setAccessible(true);
        return (Class<?>) method.invoke(loader, className);
    }
}
