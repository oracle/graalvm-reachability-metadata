/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.jersey.spi.service.ServiceFinder;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import javax.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceFinderTest {
    private static final String SERVICE_RESOURCE = "META-INF/services/" + MultivaluedMap.class.getName();

    @Test
    public void discoversProviderInstancesAndClassesFromServiceResource() {
        final ServiceFinder<MultivaluedMap> finder = ServiceFinder.find(MultivaluedMap.class);

        final MultivaluedMap[] providers = finder.toArray();
        final Class<?>[] providerClasses = finder.toClassArray();

        assertThat(providers).hasSize(1);
        assertThat(providers[0]).isInstanceOf(MultivaluedMapImpl.class);
        assertThat(providerClasses).containsExactly(MultivaluedMapImpl.class);
    }

    @Test
    public void fallsBackToServiceFinderClassLoaderWhenContextClassLoaderHasNoServiceResources() {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        final ClassLoader noServiceResourcesClassLoader = new ClassLoader(originalContextClassLoader) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (SERVICE_RESOURCE.equals(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getResources(name);
            }
        };

        currentThread.setContextClassLoader(noServiceResourcesClassLoader);
        try {
            final ServiceFinder<MultivaluedMap> finder = ServiceFinder.find(MultivaluedMap.class);

            final MultivaluedMap[] providers = finder.toArray();

            assertThat(providers).hasSize(1);
            assertThat(providers[0]).isInstanceOf(MultivaluedMapImpl.class);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }
}
