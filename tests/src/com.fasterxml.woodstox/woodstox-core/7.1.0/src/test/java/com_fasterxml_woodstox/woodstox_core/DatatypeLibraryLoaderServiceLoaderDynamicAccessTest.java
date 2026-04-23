/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibraryFactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.net.URL;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

public class DatatypeLibraryLoaderServiceLoaderDynamicAccessTest {
    private static final String LOADER_CLASS_NAME =
            "com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader$Service$Loader";
    private static final String LOADER_CLASS_CACHE_FIELD =
            "class$org$relaxng$datatype$helpers$DatatypeLibraryLoader$Service$Loader";
    private static final String SERVICE_RESOURCE =
            "META-INF/services/" + DatatypeLibraryFactory.class.getName();

    @Test
    void coversLegacyLoaderClassAndResourceResolutionPaths() throws Throwable {
        Class<?> loaderClass = Class.forName(LOADER_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(loaderClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(loaderClass, MethodType.methodType(void.class));
        MethodHandle getResources =
                lookup.findVirtual(loaderClass, "getResources", MethodType.methodType(Enumeration.class, String.class));
        MethodHandle loadClass =
                lookup.findVirtual(loaderClass, "loadClass", MethodType.methodType(Class.class, String.class));
        VarHandle cacheField = lookup.findStaticVarHandle(loaderClass, LOADER_CLASS_CACHE_FIELD, Class.class);
        Object loader = constructor.invoke();
        Class<?> previousValue = (Class<?>) cacheField.get();

        try {
            cacheField.set(null);
            Enumeration<?> classLoaderResources = (Enumeration<?>) getResources.invoke(loader, SERVICE_RESOURCE);
            assertThat(classLoaderResources.hasMoreElements()).isTrue();
            URL classLoaderResource = (URL) classLoaderResources.nextElement();
            assertThat(classLoaderResource.toExternalForm()).contains("META-INF/services");

            cacheField.set(String.class);
            Enumeration<?> systemResources = (Enumeration<?>) getResources.invoke(loader, SERVICE_RESOURCE);
            assertThat(systemResources.hasMoreElements()).isTrue();
            URL systemResource = (URL) systemResources.nextElement();
            assertThat(systemResource.toExternalForm()).contains("META-INF/services");

            Class<?> factoryClass = (Class<?>) loadClass.invoke(
                    loader, DatatypeLibraryLoaderServiceDynamicAccessTest.TestDatatypeLibraryFactory.class.getName());
            assertThat(factoryClass)
                    .isEqualTo(DatatypeLibraryLoaderServiceDynamicAccessTest.TestDatatypeLibraryFactory.class);
            assertThat(DatatypeLibraryFactory.class.isAssignableFrom(factoryClass)).isTrue();
        } finally {
            cacheField.set(previousValue);
        }
    }
}
