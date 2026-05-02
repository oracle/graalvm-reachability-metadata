/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_osgi_locator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.geronimo.osgi.locator.ProviderLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Geronimo_osgi_locatorTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetProviderLocator() {
        ProviderLocator.destroy();
    }

    @Test
    void returnsEmptyResultsWhenRegistryAndServiceDefinitionAreUnavailable() throws Exception {
        ResourceBackedClassLoader emptyClassLoader = new ResourceBackedClassLoader(Map.of());

        assertThat(ProviderLocator.locate(TestService.class.getName())).isNull();
        assertThat(ProviderLocator.locateAll(TestService.class.getName())).isEmpty();
        assertThat(ProviderLocator.getServiceClass(
                TestService.class.getName(), Geronimo_osgi_locatorTest.class, emptyClassLoader)).isNull();
        assertThat(ProviderLocator.getService(
                TestService.class.getName(), Geronimo_osgi_locatorTest.class, emptyClassLoader)).isNull();
        assertThat(ProviderLocator.getServiceClasses(
                TestService.class.getName(), Geronimo_osgi_locatorTest.class, emptyClassLoader)).isEmpty();
        assertThat(ProviderLocator.getServices(
                TestService.class.getName(), Geronimo_osgi_locatorTest.class, emptyClassLoader)).isEmpty();
    }

    @Test
    void loadsOrdinaryClassesUsingCurrentAndFallbackClassLoaders() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        ResourceBackedClassLoader contextClassLoader = new ResourceBackedClassLoader(Map.of());
        thread.setContextClassLoader(contextClassLoader);
        try {
            assertThat(ProviderLocator.loadClass(PrimaryProvider.class.getName())).isSameAs(PrimaryProvider.class);
            assertThat(ProviderLocator.loadClass(
                    SecondaryProvider.class.getName(), Geronimo_osgi_locatorTest.class, null))
                    .isSameAs(SecondaryProvider.class);
            assertThatThrownBy(() -> ProviderLocator.loadClass(
                    "example.DoesNotExist", Geronimo_osgi_locatorTest.class, contextClassLoader))
                    .isInstanceOf(ClassNotFoundException.class);
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void readsFirstProviderClassFromServiceDefinitionAndCreatesInstance() throws Exception {
        URL serviceDefinition = writeServiceDefinition(
                "first-provider",
                "# ignored comment\n"
                        + "   \n"
                        + PrimaryProvider.class.getName() + "   # trailing comments are ignored\n"
                        + SecondaryProvider.class.getName() + "\n");
        ResourceBackedClassLoader classLoader = new ResourceBackedClassLoader(
                Map.of(serviceResourceName(), List.of(serviceDefinition)));

        Class<?> serviceClass = ProviderLocator.getServiceClass(TestService.class.getName(), null, classLoader);
        Object service = ProviderLocator.getService(TestService.class.getName(), null, classLoader);

        assertThat(serviceClass).isSameAs(PrimaryProvider.class);
        assertThat(service).isInstanceOf(PrimaryProvider.class);
        assertThat(((TestService) service).value()).isEqualTo("primary");
    }

    @Test
    void loadsDiscoveredProviderClassFromCallerClassLoaderWhenResourceClassLoaderCannotLoadIt() throws Exception {
        URL serviceDefinition = writeServiceDefinition("caller-loader-provider", CallerLoadedProvider.class.getName());
        ResourceBackedClassLoader classLoader = new ResourceBackedClassLoader(
                Map.of(serviceResourceName(), List.of(serviceDefinition)),
                List.of(CallerLoadedProvider.class.getName()));

        Class<?> serviceClass = ProviderLocator.getServiceClass(
                TestService.class.getName(), Geronimo_osgi_locatorTest.class, classLoader);
        Object service = ProviderLocator.getService(
                TestService.class.getName(), Geronimo_osgi_locatorTest.class, classLoader);

        assertThat(serviceClass).isSameAs(CallerLoadedProvider.class);
        assertThat(service).isInstanceOf(CallerLoadedProvider.class);
        assertThat(((TestService) service).value()).isEqualTo("caller");
    }

    @Test
    void readsAllServiceDefinitionsInOrderAndDeduplicatesProviderClasses() throws Exception {
        URL firstServiceDefinition = writeServiceDefinition(
                "ordered-providers-1",
                PrimaryProvider.class.getName() + "\n"
                        + "# a blank line follows\n"
                        + "\n"
                        + SecondaryProvider.class.getName() + "\n");
        URL secondServiceDefinition = writeServiceDefinition(
                "ordered-providers-2",
                SecondaryProvider.class.getName() + "\n"
                        + PrimaryProvider.class.getName() + "\n");
        ResourceBackedClassLoader classLoader = new ResourceBackedClassLoader(
                Map.of(serviceResourceName(), List.of(firstServiceDefinition, secondServiceDefinition)));

        List<Class<?>> serviceClasses = ProviderLocator.getServiceClasses(
                TestService.class.getName(), null, classLoader);
        List<Object> services = ProviderLocator.getServices(TestService.class.getName(), null, classLoader);

        assertThat(serviceClasses).containsExactly(PrimaryProvider.class, SecondaryProvider.class);
        assertThat(services).hasSize(2);
        assertThat(services)
                .extracting(service -> ((TestService) service).value())
                .containsExactly("primary", "secondary");
    }

    @Test
    void continuesServiceDiscoveryAfterUnreadableServiceDefinition() throws Exception {
        URL unreadableServiceDefinition = unreadableServiceDefinition("unreadable-providers");
        URL readableServiceDefinition = writeServiceDefinition("readable-providers", PrimaryProvider.class.getName());
        ResourceBackedClassLoader classLoader = new ResourceBackedClassLoader(
                Map.of(serviceResourceName(), List.of(unreadableServiceDefinition, readableServiceDefinition)));

        List<Class<?>> serviceClasses = ProviderLocator.getServiceClasses(
                TestService.class.getName(), null, classLoader);

        assertThat(serviceClasses).containsExactly(PrimaryProvider.class);
    }

    @Test
    void readsPropertiesFromConfiguredJavaHomeRelativeFile() throws Exception {
        Path relativePropertiesPath = Path.of("conf", "locator.properties");
        Path missingPropertiesPath = Path.of("conf", "missing.properties");
        Path propertiesFile = Files.createDirectories(tempDir.resolve("conf")).resolve("locator.properties");
        Properties properties = new Properties();
        properties.setProperty("provider", PrimaryProvider.class.getName());
        try (OutputStream outputStream = Files.newOutputStream(propertiesFile)) {
            properties.store(outputStream, "test properties");
        }

        String originalJavaHome = System.getProperty("java.home");
        System.setProperty("java.home", tempDir.toString());
        try {
            assertThat(ProviderLocator.lookupByJREPropertyFile(relativePropertiesPath.toString(), "provider"))
                    .isEqualTo(PrimaryProvider.class.getName());
            assertThat(ProviderLocator.lookupByJREPropertyFile(relativePropertiesPath.toString(), "missing")).isNull();
            assertThat(ProviderLocator.lookupByJREPropertyFile(missingPropertiesPath.toString(), "provider")).isNull();
        } finally {
            if (originalJavaHome == null) {
                System.clearProperty("java.home");
            } else {
                System.setProperty("java.home", originalJavaHome);
            }
        }
    }

    private URL writeServiceDefinition(String directoryName, String contents) throws IOException {
        Path servicesDirectory = Files.createDirectories(tempDir.resolve(directoryName).resolve("META-INF/services"));
        Path serviceDefinition = servicesDirectory.resolve(TestService.class.getName());
        Files.writeString(serviceDefinition, contents, StandardCharsets.UTF_8);
        return serviceDefinition.toUri().toURL();
    }

    private URL unreadableServiceDefinition(String path) throws IOException {
        return URL.of(URI.create("test://service-definitions/" + path), new UnreadableUrlStreamHandler());
    }

    private String serviceResourceName() {
        return "META-INF/services/" + TestService.class.getName();
    }

    public interface TestService {
        String value();
    }

    public static class PrimaryProvider implements TestService {
        public PrimaryProvider() {
        }

        @Override
        public String value() {
            return "primary";
        }
    }

    public static class SecondaryProvider implements TestService {
        public SecondaryProvider() {
        }

        @Override
        public String value() {
            return "secondary";
        }
    }

    public static class CallerLoadedProvider implements TestService {
        public CallerLoadedProvider() {
        }

        @Override
        public String value() {
            return "caller";
        }
    }

    private static final class UnreadableUrlStreamHandler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    throw new IOException("Service definition is not readable");
                }
            };
        }
    }

    private static final class ResourceBackedClassLoader extends ClassLoader {
        private final Map<String, List<URL>> resources;
        private final List<String> unavailableClasses;

        private ResourceBackedClassLoader(Map<String, List<URL>> resources) {
            this(resources, List.of());
        }

        private ResourceBackedClassLoader(Map<String, List<URL>> resources, List<String> unavailableClasses) {
            super(Geronimo_osgi_locatorTest.class.getClassLoader());
            this.resources = resources;
            this.unavailableClasses = unavailableClasses;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (unavailableClasses.contains(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> matchingResources = resources.get(name);
            if (matchingResources != null) {
                return Collections.enumeration(matchingResources);
            }
            return super.getResources(name);
        }
    }
}
