/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.spi.service.ServiceFinder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

public class ServiceFinderInnerLazyObjectIteratorTest {
    private static final String OBJECT_SERVICE_RESOURCE = "META-INF/services/java.lang.Object";
    private static final String PROVIDER_CLASS_NAME = ServiceFinderInnerLazyObjectIteratorTest.class.getName();

    @Test
    void hasNextInstantiatesProviderFromServiceConfiguration() throws Exception {
        final ClassLoader serviceLoader = new SingleServiceConfigurationClassLoader(
                ServiceFinderInnerLazyObjectIteratorTest.class.getClassLoader(),
                OBJECT_SERVICE_RESOURCE,
                PROVIDER_CLASS_NAME);
        final Iterator<Object> services = ServiceFinder.find(Object.class, serviceLoader).iterator();

        assertThat(services.hasNext()).isTrue();
        final Object service = services.next();

        assertThat(service).isInstanceOf(ServiceFinderInnerLazyObjectIteratorTest.class);
        assertThat(services.hasNext()).isFalse();
    }

    private static final class SingleServiceConfigurationClassLoader extends ClassLoader {
        private final String resourceName;
        private final String serviceConfiguration;

        private SingleServiceConfigurationClassLoader(
                final ClassLoader parent,
                final String resourceName,
                final String providerClassName) {
            super(parent);
            this.resourceName = resourceName;
            this.serviceConfiguration = providerClassName + System.lineSeparator();
        }

        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            if (!resourceName.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(Collections.singletonList(newServiceConfigurationUrl()));
        }

        private URL newServiceConfigurationUrl() throws IOException {
            return new URL(null, "memory:" + resourceName, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(final URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                            connected = true;
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(serviceConfiguration.getBytes(StandardCharsets.UTF_8));
                        }
                    };
                }
            });
        }
    }
}
