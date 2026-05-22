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
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Collections;
import java.util.Enumeration;
import javax.ws.rs.core.MultivaluedMap;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceFinderTest {
    private static final String SERVICE_FINDER_CLASS_NAME = ServiceFinder.class.getName();
    private static final String SERVICE_FINDER_CLASS_RESOURCE = SERVICE_FINDER_CLASS_NAME.replace('.', '/') + ".class";
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

    @Test
    public void fallsBackToServiceFinderClassLoaderWhenInitialLoaderCannotFindClassResource() throws Exception {
        try (FallbackTrackingClassLoader classLoader = new FallbackTrackingClassLoader(
                new URL[] {codeSourceUrl(ServiceFinder.class)},
                ServiceFinderTest.class.getClassLoader())) {
            final Class<?> isolatedServiceFinder = Class.forName(SERVICE_FINDER_CLASS_NAME, true, classLoader);

            assertThat(isolatedServiceFinder.getName()).isEqualTo(SERVICE_FINDER_CLASS_NAME);
            assertThat(classLoader.getRejectedClassResourceRequests()).isEqualTo(1);
            assertThat(classLoader.getFallbackClassResourceRequests()).isGreaterThanOrEqualTo(1);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        final CodeSource codeSource = type.getProtectionDomain().getCodeSource();

        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static final class FallbackTrackingClassLoader extends URLClassLoader {
        private int rejectedClassResourceRequests;
        private int fallbackClassResourceRequests;

        private FallbackTrackingClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
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
        public URL getResource(String name) {
            if (SERVICE_FINDER_CLASS_RESOURCE.equals(name) && rejectedClassResourceRequests == 0) {
                rejectedClassResourceRequests++;
                return null;
            }
            if (SERVICE_FINDER_CLASS_RESOURCE.equals(name)) {
                fallbackClassResourceRequests++;
            }
            return super.getResource(name);
        }

        private int getRejectedClassResourceRequests() {
            return rejectedClassResourceRequests;
        }

        private int getFallbackClassResourceRequests() {
            return fallbackClassResourceRequests;
        }
    }
}
