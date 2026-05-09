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
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ServiceFinderTest {
    private static final String SERVICE_FINDER_CLASS_NAME = "com.sun.jersey.spi.service.ServiceFinder";
    private static final String SERVICE_FINDER_RESOURCE_NAME = "com/sun/jersey/spi/service/ServiceFinder.class";

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
    void initializesIsolatedCopyWhenItsClassResourceIsHidden() throws Exception {
        try {
            final ClassLoader classLoader = new ServiceFinderResourceHidingClassLoader();
            final Class<?> serviceFinderClass = Class.forName(SERVICE_FINDER_CLASS_NAME, true, classLoader);

            assertThat(serviceFinderClass.getName()).isEqualTo(SERVICE_FINDER_CLASS_NAME);
            assertThat(serviceFinderClass.getClassLoader()).isSameAs(classLoader);
        } catch (final Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
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

    private static final class ServiceFinderResourceHidingClassLoader extends ClassLoader {
        private ServiceFinderResourceHidingClassLoader() {
            super(ServiceFinderTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (SERVICE_FINDER_CLASS_NAME.equals(name)) {
                        loadedClass = findClass(name);
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            if (!SERVICE_FINDER_CLASS_NAME.equals(name)) {
                return super.findClass(name);
            }

            try (InputStream inputStream = getParent().getResourceAsStream(SERVICE_FINDER_RESOURCE_NAME)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                final byte[] bytes = inputStream.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (final IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        @Override
        public URL getResource(final String name) {
            if (SERVICE_FINDER_RESOURCE_NAME.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }
    }
}
