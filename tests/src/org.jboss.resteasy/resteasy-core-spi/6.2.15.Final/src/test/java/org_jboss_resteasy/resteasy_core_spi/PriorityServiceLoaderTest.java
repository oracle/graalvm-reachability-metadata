/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Priority;

import org.jboss.resteasy.spi.PriorityServiceLoader;
import org.junit.jupiter.api.Test;

public class PriorityServiceLoaderTest {
    @Test
    void loadDiscoversProviderConfigurationAndSortsImplementationsByPriority() throws Exception {
        ServiceResourceClassLoader classLoader = new ServiceResourceClassLoader(
                PriorityServiceLoaderService.class,
                List.of(LowPriorityService.class, HighPriorityService.class));

        PriorityServiceLoader<PriorityServiceLoaderService> loader = PriorityServiceLoader.load(
                PriorityServiceLoaderService.class,
                classLoader);

        Set<Class<PriorityServiceLoaderService>> types = loader.getTypes();
        assertThat(types.stream().map(Class::getName))
                .containsExactly(HighPriorityService.class.getName(), LowPriorityService.class.getName());
        assertThat(classLoader.requestedResources())
                .containsExactly("META-INF/services/" + PriorityServiceLoaderService.class.getName());
        assertThat(classLoader.loadedServiceClassNames())
                .containsExactly(LowPriorityService.class.getName(), HighPriorityService.class.getName());
    }

    private static final class ServiceResourceClassLoader extends ClassLoader {
        private final Class<?> serviceType;
        private final List<Class<?>> implementationTypes;
        private final List<String> requestedResources;
        private final List<String> loadedServiceClassNames;
        private final String serviceConfiguration;

        private ServiceResourceClassLoader(Class<?> serviceType, List<Class<?>> implementationTypes) {
            super(PriorityServiceLoaderTest.class.getClassLoader());
            this.serviceType = serviceType;
            this.implementationTypes = List.copyOf(implementationTypes);
            requestedResources = new ArrayList<>();
            loadedServiceClassNames = new ArrayList<>();
            serviceConfiguration = implementationTypes.stream()
                    .map(Class::getName)
                    .reduce("# service implementations\n", (current, className) -> current + className + "\n");
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResources.add(name);
            String expectedName = "META-INF/services/" + serviceType.getName();
            if (!expectedName.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(List.of(serviceConfigurationUrl()));
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (implementationTypes.stream().map(Class::getName).anyMatch(name::equals)) {
                loadedServiceClassNames.add(name);
            }
            return super.loadClass(name);
        }

        private List<String> requestedResources() {
            return requestedResources;
        }

        private List<String> loadedServiceClassNames() {
            return loadedServiceClassNames;
        }

        private URL serviceConfigurationUrl() throws IOException {
            return new URL(null, "memory:priority-service-loader", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
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

interface PriorityServiceLoaderService {
}

@Priority(5)
final class HighPriorityService implements PriorityServiceLoaderService {
}

@Priority(20)
final class LowPriorityService implements PriorityServiceLoaderService {
}
