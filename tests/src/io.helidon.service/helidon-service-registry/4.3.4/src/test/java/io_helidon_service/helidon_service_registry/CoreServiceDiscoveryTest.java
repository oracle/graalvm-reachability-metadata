/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_service.helidon_service_registry;

import java.util.List;

import io.helidon.service.registry.DescriptorHandler;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceRegistryConfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CoreServiceDiscoveryTest {
    @Test
    void discoversDescriptorsUsingRegistryClassLoaderFallback() {
        ServiceRegistryConfig config = ServiceRegistryConfig.builder()
                .discoverServices(true)
                .discoverServicesFromServiceLoader(false)
                .build();
        List<DescriptorHandler> metadata = ServiceDiscovery.create(config).allMetadata();

        assertThat(metadata).isNotEmpty();

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new RejectingClassLoader());

            List<ServiceDescriptor<?>> descriptors = metadata.stream()
                    .map(DescriptorHandler::descriptor)
                    .toList();

            assertThat(descriptors)
                    .extracting(descriptor -> descriptor.descriptorType().fqName())
                    .contains("io.helidon.service.registry.ServiceRegistry__ServiceDescriptor");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
