/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibrary;
import com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

public class DatatypeLibraryLoaderServiceLoader2DynamicAccessTest {
    private static final String SERVICE_RESOURCE =
            "META-INF/services/com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibraryFactory";
    private static final String PROVIDER_CLASS_NAME =
            DatatypeLibraryLoaderServiceDynamicAccessTest.TestDatatypeLibraryFactory.class.getName();

    @Test
    void usesTheContextClassLoaderWhenResolvingServiceProviders() throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        TrackingContextClassLoader contextLoader =
                new TrackingContextClassLoader(DatatypeLibraryLoaderServiceLoader2DynamicAccessTest.class.getClassLoader());
        try {
            Thread.currentThread().setContextClassLoader(contextLoader);

            DatatypeLibrary library = new DatatypeLibraryLoader()
                    .createDatatypeLibrary(DatatypeLibraryLoaderServiceDynamicAccessTest.SERVICE_URI);

            assertThat(library).isNotNull();
            assertThat(library.createDatatype("token").isValid("loader2", null)).isTrue();
            assertThat(contextLoader.requestedServiceResource()).isEqualTo(SERVICE_RESOURCE);
            assertThat(contextLoader.loadedProviderClassName()).isEqualTo(PROVIDER_CLASS_NAME);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
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
