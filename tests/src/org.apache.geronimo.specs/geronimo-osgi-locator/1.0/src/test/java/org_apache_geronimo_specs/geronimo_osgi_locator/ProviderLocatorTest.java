/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_osgi_locator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.geronimo.osgi.locator.ProviderLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

public class ProviderLocatorTest {
    private static final String SERVICE = Callable.class.getName();
    private static final ClassLoader LOADER = ProviderLocatorTest.class.getClassLoader();

    @Test
    void loadsClassesThroughEachPublicOverload() throws Exception {
        String providerName = GreetingProvider.class.getName();

        assertThat(ProviderLocator.loadClass(providerName)).isSameAs(GreetingProvider.class);
        assertThat(ProviderLocator.loadClass(providerName, ProviderLocatorTest.class)).isSameAs(GreetingProvider.class);
        assertThat(ProviderLocator.loadClass(providerName, null, LOADER)).isSameAs(GreetingProvider.class);
        assertThat(ProviderLocator.loadClass(providerName, ProviderLocatorTest.class, null)).isSameAs(GreetingProvider.class);
    }

    @Test
    void discoversAndInvokesTheFirstService() throws Exception {
        assertThat(ProviderLocator.getServiceClass(SERVICE, null, LOADER)).isSameAs(GreetingProvider.class);

        Object service = ProviderLocator.getService(SERVICE, null, LOADER);

        assertThat(service).isInstanceOf(Callable.class);
        assertThat(((Callable<?>) service).call()).isEqualTo("Hello from the greeting provider");
    }

    @Test
    void discoversServicesUsingTheRequestingClass() throws Exception {
        assertThat(ProviderLocator.getServiceClass(SERVICE, ProviderLocatorTest.class, null)).isSameAs(GreetingProvider.class);

        Object service = ProviderLocator.getService(SERVICE, ProviderLocatorTest.class, null);

        assertThat(service).isInstanceOf(Callable.class);
        assertThat(((Callable<?>) service).call()).isEqualTo("Hello from the greeting provider");
        assertThat(ProviderLocator.getServiceClasses(SERVICE, ProviderLocatorTest.class, null))
                .containsExactly(GreetingProvider.class, FarewellProvider.class);
    }

    @Test
    void discoversDistinctServiceClassesInDeclarationOrder() throws Exception {
        List<Class<?>> classes = ProviderLocator.getServiceClasses(SERVICE, ProviderLocatorTest.class, LOADER);

        assertThat(classes).containsExactly(GreetingProvider.class, FarewellProvider.class);
    }

    @Test
    void instantiatesAndInvokesEveryDistinctService() throws Exception {
        List<Object> services = ProviderLocator.getServices(SERVICE, ProviderLocatorTest.class, LOADER);

        assertThat(services).hasSize(2);
        assertThat(services.get(0)).isInstanceOf(GreetingProvider.class);
        assertThat(services.get(1)).isInstanceOf(FarewellProvider.class);
        assertThat(((Callable<?>) services.get(0)).call()).isEqualTo("Hello from the greeting provider");
        assertThat(((Callable<?>) services.get(1)).call()).isEqualTo("Goodbye from the farewell provider");
    }

    @Test
    void returnsNoProvidersForAnUnregisteredService() throws Exception {
        String unregisteredService = "example.unregistered.Service";

        assertThat(ProviderLocator.locate(unregisteredService)).isNull();
        assertThat(ProviderLocator.locateAll(unregisteredService)).isEmpty();
        assertThat(ProviderLocator.getServiceClass(unregisteredService, ProviderLocatorTest.class, LOADER)).isNull();
        assertThat(ProviderLocator.getService(unregisteredService, ProviderLocatorTest.class, LOADER)).isNull();
        assertThat(ProviderLocator.getServiceClasses(unregisteredService, ProviderLocatorTest.class, LOADER)).isEmpty();
        assertThat(ProviderLocator.getServices(unregisteredService, ProviderLocatorTest.class, LOADER)).isEmpty();
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void readsProviderConfigurationFromTheJavaHomeDirectory(@TempDir Path javaHome) throws Exception {
        Files.writeString(javaHome.resolve("locator.properties"), """
                # Application provider configuration
                example.provider=example.configured.Provider
                """);
        String originalJavaHome = System.getProperty("java.home");
        try {
            System.setProperty("java.home", javaHome.toString());

            assertThat(ProviderLocator.lookupByJREPropertyFile("locator.properties", "example.provider"))
                    .isEqualTo("example.configured.Provider");
            assertThat(ProviderLocator.lookupByJREPropertyFile("locator.properties", "example.absent")).isNull();
            assertThat(ProviderLocator.lookupByJREPropertyFile("absent.properties", "example.provider")).isNull();
        } finally {
            if (originalJavaHome == null) {
                System.clearProperty("java.home");
            } else {
                System.setProperty("java.home", originalJavaHome);
            }
        }
    }
}
