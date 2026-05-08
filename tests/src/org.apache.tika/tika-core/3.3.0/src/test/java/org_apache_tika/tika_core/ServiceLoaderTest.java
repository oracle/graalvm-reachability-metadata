/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.config.ServiceLoader;

public class ServiceLoaderTest {
    private static final String RESOURCE_NAME = "service-loader-test-resource.txt";
    private static final String RESOURCE_VALUE = "service-loader-resource-value";

    @TempDir
    private Path tempDir;

    @Test
    public void getsResourceAsStreamFromConfiguredClassLoader() throws Exception {
        ServiceLoader serviceLoader = new ServiceLoader(new ServiceResourceClassLoader(
                ServiceLoaderTest.class.getClassLoader(), Collections.emptyList()));

        try (InputStream stream = serviceLoader.getResourceAsStream(RESOURCE_NAME)) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes(), UTF_8)).isEqualTo(RESOURCE_VALUE);
        }
    }

    @Test
    public void loadsStaticServiceProvidersDeclaredByServiceResource() throws Exception {
        Path serviceFile = createServiceFile(TestService.class, TestServiceProvider.class);
        ServiceLoader serviceLoader = new ServiceLoader(new ServiceResourceClassLoader(
                ServiceLoaderTest.class.getClassLoader(),
                List.of(serviceFile.toUri().toURL())), LoadErrorHandler.THROW);

        List<TestService> providers = serviceLoader.loadStaticServiceProviders(TestService.class);

        assertThat(providers).hasSize(1);
        assertThat(providers.get(0)).isInstanceOf(TestServiceProvider.class);
        assertThat(providers.get(0).value()).isEqualTo("loaded-from-service-resource");
    }

    private Path createServiceFile(Class<?> serviceInterface, Class<?> provider)
            throws IOException {
        Path servicesDirectory = tempDir.resolve("META-INF").resolve("services");
        Files.createDirectories(servicesDirectory);
        Path serviceFile = servicesDirectory.resolve(serviceInterface.getName());
        Files.writeString(serviceFile, provider.getName() + System.lineSeparator(), UTF_8);
        return serviceFile;
    }

    public interface TestService {
        String value();
    }

    public static class TestServiceProvider implements TestService {
        public TestServiceProvider() {
        }

        @Override
        public String value() {
            return "loaded-from-service-resource";
        }
    }

    private static class ServiceResourceClassLoader extends ClassLoader {
        private final List<URL> serviceResources;

        ServiceResourceClassLoader(ClassLoader parent, List<URL> serviceResources) {
            super(parent);
            this.serviceResources = serviceResources;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RESOURCE_NAME.equals(name)) {
                return new ByteArrayInputStream(RESOURCE_VALUE.getBytes(UTF_8));
            }
            return super.getResourceAsStream(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (("META-INF/services/" + TestService.class.getName()).equals(name)) {
                return Collections.enumeration(serviceResources);
            }
            return super.getResources(name);
        }
    }
}
