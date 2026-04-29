/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_osgi_locator;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.geronimo.osgi.locator.ProviderLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Geronimo_osgi_locatorTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearLocatorState() {
        ProviderLocator.destroy();
    }

    @Test
    void loadClassUsesExplicitLoaderBeforeContextClassFallback() throws Exception {
        ClassLoader currentLoader = Geronimo_osgi_locatorTest.class.getClassLoader();

        assertThat(ProviderLocator.loadClass(AlphaProvider.class.getName(), null, currentLoader))
                .isEqualTo(AlphaProvider.class);
        assertThat(ProviderLocator.loadClass(BetaProvider.class.getName(), Geronimo_osgi_locatorTest.class,
                new RejectingClassLoader()))
                .isEqualTo(BetaProvider.class);
    }

    @Test
    void loadClassUsesThreadContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(Geronimo_osgi_locatorTest.class.getClassLoader());
        try {
            assertThat(ProviderLocator.loadClass(GreetingService.class.getName())).isEqualTo(GreetingService.class);
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    @Test
    void serviceLookupReadsFirstNonCommentProviderFromMetaInfServices() throws Exception {
        Path classPathRoot = Files.createDirectory(tempDir.resolve("single-provider"));
        writeServiceDefinition(classPathRoot, GreetingService.class,
                "# leading comment\n"
                        + "\n"
                        + "  " + AlphaProvider.class.getName() + "  # inline comment\n"
                        + BetaProvider.class.getName() + "\n");

        try (URLClassLoader loader = newServiceLoader(classPathRoot)) {
            Class<?> serviceClass = ProviderLocator.getServiceClass(GreetingService.class.getName(),
                    Geronimo_osgi_locatorTest.class, loader);
            Object service = ProviderLocator.getService(GreetingService.class.getName(),
                    Geronimo_osgi_locatorTest.class, loader);

            assertThat(serviceClass).isEqualTo(AlphaProvider.class);
            assertThat(service).isInstanceOf(AlphaProvider.class);
            assertThat(((GreetingService) service).name()).isEqualTo("alpha");
        }
    }

    @Test
    void serviceLookupsReturnAllDistinctProvidersInDefinitionOrder() throws Exception {
        Path firstRoot = Files.createDirectory(tempDir.resolve("first-root"));
        Path secondRoot = Files.createDirectory(tempDir.resolve("second-root"));
        writeServiceDefinition(firstRoot, GreetingService.class,
                AlphaProvider.class.getName() + "\n"
                        + BetaProvider.class.getName() + "\n"
                        + AlphaProvider.class.getName() + " # duplicate is ignored by class lookup\n");
        writeServiceDefinition(secondRoot, GreetingService.class,
                "# another service resource\n"
                        + BetaProvider.class.getName() + "\n"
                        + GammaProvider.class.getName() + "\n");

        try (URLClassLoader loader = newServiceLoader(firstRoot, secondRoot)) {
            List<Class<?>> serviceClasses = ProviderLocator.getServiceClasses(GreetingService.class.getName(),
                    Geronimo_osgi_locatorTest.class, loader);
            List<Object> services = ProviderLocator.getServices(GreetingService.class.getName(),
                    Geronimo_osgi_locatorTest.class, loader);

            assertThat(serviceClasses).containsExactly(AlphaProvider.class, BetaProvider.class, GammaProvider.class);
            assertThat(serviceNames(services)).containsExactly("alpha", "beta", "gamma");
        }
    }

    @Test
    void missingServiceDefinitionsReturnNullAndEmptyLists() throws Exception {
        Path emptyRoot = Files.createDirectory(tempDir.resolve("empty-root"));

        try (URLClassLoader loader = newServiceLoader(emptyRoot)) {
            assertThat(ProviderLocator.getServiceClass(MissingService.class.getName(), Geronimo_osgi_locatorTest.class,
                    loader)).isNull();
            assertThat(ProviderLocator.getService(MissingService.class.getName(), Geronimo_osgi_locatorTest.class,
                    loader)).isNull();
            assertThat(ProviderLocator.getServiceClasses(MissingService.class.getName(),
                    Geronimo_osgi_locatorTest.class, loader)).isEmpty();
            assertThat(ProviderLocator.getServices(MissingService.class.getName(), Geronimo_osgi_locatorTest.class,
                    loader)).isEmpty();
        }
    }

    @Test
    void locatorReturnsEmptyResultsWhenNoOsgiRegistryIsAvailable() {
        assertThat(ProviderLocator.locate(AlphaProvider.class.getName())).isNull();
        assertThat(ProviderLocator.locateAll(AlphaProvider.class.getName())).isEmpty();
    }

    @Test
    void jrePropertyFileLookupReadsPropertiesFromCurrentJavaHome() throws Exception {
        String originalJavaHome = System.getProperty("java.home");
        System.setProperty("java.home", tempDir.toString());
        try {
            Path configurationDirectory = Files.createDirectories(tempDir.resolve("conf"));
            Files.writeString(configurationDirectory.resolve("providers.properties"),
                    "provider=" + AlphaProvider.class.getName() + "\n"
                            + "description=Geronimo locator test\n",
                    StandardCharsets.UTF_8);

            assertThat(ProviderLocator.lookupByJREPropertyFile("conf/providers.properties", "provider"))
                    .isEqualTo(AlphaProvider.class.getName());
            assertThat(ProviderLocator.lookupByJREPropertyFile("conf/providers.properties", "unknown")).isNull();
            assertThat(ProviderLocator.lookupByJREPropertyFile("conf/missing.properties", "provider")).isNull();
        } finally {
            System.setProperty("java.home", originalJavaHome);
        }
    }

    private static void writeServiceDefinition(Path root, Class<?> serviceType, String contents) throws Exception {
        Path servicesDirectory = Files.createDirectories(root.resolve("META-INF").resolve("services"));
        Files.writeString(servicesDirectory.resolve(serviceType.getName()), contents, StandardCharsets.UTF_8);
    }

    private static URLClassLoader newServiceLoader(Path... roots) throws Exception {
        URL[] urls = new URL[roots.length];
        for (int index = 0; index < roots.length; index++) {
            urls[index] = roots[index].toUri().toURL();
        }
        return new URLClassLoader(urls, Geronimo_osgi_locatorTest.class.getClassLoader());
    }

    private static List<String> serviceNames(List<Object> services) {
        List<String> names = new ArrayList<>();
        for (Object service : services) {
            names.add(((GreetingService) service).name());
        }
        return names;
    }

    public interface GreetingService {
        String name();
    }

    public interface MissingService {
    }

    public static class AlphaProvider implements GreetingService {
        @Override
        public String name() {
            return "alpha";
        }
    }

    public static class BetaProvider implements GreetingService {
        @Override
        public String name() {
            return "beta";
        }
    }

    public static class GammaProvider implements GreetingService {
        @Override
        public String name() {
            return "gamma";
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
