/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.spi.service.ServiceFinder;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

public class ServiceFinderTest {
    private static final String MISSING_RESOURCE_NAME = "META-INF/services/com_sun_jersey.missing.ServiceFinderTest";

    @Test
    void createsTypedArraysWhenNoServiceConfigurationExists() {
        final ClassLoader emptyServiceLoader = new EmptyServiceConfigurationClassLoader();
        final ServiceFinder<TestService> finder = ServiceFinder.find(TestService.class, emptyServiceLoader);

        final TestService[] services = finder.toArray();
        final Class<TestService>[] serviceClasses = finder.toClassArray();

        assertThat(services).isEmpty();
        assertThat(services.getClass().getComponentType()).isEqualTo(TestService.class);
        assertThat(serviceClasses).isEmpty();
        assertThat(serviceClasses.getClass().getComponentType()).isEqualTo(Class.class);
    }

    @Test
    void resolvesMissingResourceWithServiceFinderClassLoaderFallback() throws Exception {
        final Method getResource = ServiceFinder.class.getDeclaredMethod("getResource", String.class);
        getResource.setAccessible(true);

        final URL resource = (URL) getResource.invoke(null, MISSING_RESOURCE_NAME);

        assertThat(resource).isNull();
    }

    public interface TestService {
    }

    private static final class EmptyServiceConfigurationClassLoader extends ClassLoader {
        private EmptyServiceConfigurationClassLoader() {
            super(ServiceFinderTest.class.getClassLoader());
        }

        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            return Collections.emptyEnumeration();
        }
    }
}
