/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.util.ServiceLoader;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ServiceLoaderTest {
    private static final String SERVICES_PREFIX = "META-INF/services/";

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadInstantiatesImplementationListedByServiceResource() throws IOException {
        DiscoveredService.constructedCount = 0;
        Path serviceFile = temporaryDirectory.resolve("service-provider.txt");
        Files.writeString(serviceFile, DiscoveredService.class.getName() + "\n", StandardCharsets.UTF_8);
        ClassLoader classLoader = new SingleServiceResourceClassLoader(
                ServiceContract.class.getClassLoader(),
                SERVICES_PREFIX + ServiceContract.class.getName(),
                serviceFile.toUri().toURL());

        Set<ServiceContract> services = ServiceLoader.load(ServiceContract.class, classLoader);

        assertThat(services).hasSize(1);
        assertThat(services.iterator().next()).isInstanceOf(DiscoveredService.class);
        assertThat(DiscoveredService.constructedCount).isEqualTo(1);
    }

    public interface ServiceContract {
    }

    public static class DiscoveredService implements ServiceContract {
        static int constructedCount;

        public DiscoveredService() {
            constructedCount++;
        }
    }

    private static final class SingleServiceResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL serviceResource;

        SingleServiceResourceClassLoader(ClassLoader parent, String resourceName, URL serviceResource) {
            super(parent);
            this.resourceName = resourceName;
            this.serviceResource = serviceResource;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (resourceName.equals(name)) {
                return Collections.enumeration(List.of(serviceResource));
            }
            return super.getResources(name);
        }
    }
}
