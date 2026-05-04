/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_aayushatharva_brotli4j.service;

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
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.aayushatharva.brotli4j.service.BrotliNativeProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceTest {

    @Test
    void platformNameCanBeSuppliedByConcreteImplementation() {
        BrotliNativeProvider provider = new FixedBrotliNativeProvider("linux-x86_64");

        assertThat(provider.platformName()).isEqualTo("linux-x86_64");
        assertThat(readPlatformName(provider)).isEqualTo("linux-x86_64");
    }

    @Test
    void platformNameCanBeSuppliedByLambdaImplementation() {
        BrotliNativeProvider provider = () -> "darwin-aarch64";

        assertThat(provider.platformName()).isEqualTo("darwin-aarch64");
        assertThat(readPlatformName(provider)).isEqualTo("darwin-aarch64");
    }

    @Test
    void providersCanBeSelectedByTheirPlatformName() {
        List<BrotliNativeProvider> providers = List.of(
                new FixedBrotliNativeProvider("linux-x86_64"),
                new FixedBrotliNativeProvider("windows-x86_64"),
                new FixedBrotliNativeProvider("linux-aarch64"));

        List<String> linuxPlatforms = providers.stream()
                .map(BrotliNativeProvider::platformName)
                .filter(platformName -> platformName.startsWith("linux-"))
                .collect(Collectors.toList());

        assertThat(linuxPlatforms).containsExactly("linux-x86_64", "linux-aarch64");
    }

    @Test
    void serviceArtifactDoesNotRegisterProviderImplementationsByItself() {
        ServiceLoader<BrotliNativeProvider> providers = ServiceLoader.load(BrotliNativeProvider.class);

        List<String> platformNames = StreamSupport.stream(providers.spliterator(), false)
                .map(BrotliNativeProvider::platformName)
                .collect(Collectors.toList());

        assertThat(platformNames).isEmpty();
    }

    @Test
    void providerImplementationsCanBeDiscoveredFromServiceConfiguration() {
        ServiceConfigurationClassLoader classLoader = new ServiceConfigurationClassLoader(
                ServiceTest.class.getClassLoader(), DiscoverableBrotliNativeProvider.class.getName());
        ServiceLoader<BrotliNativeProvider> providers = ServiceLoader.load(BrotliNativeProvider.class, classLoader);

        List<Class<? extends BrotliNativeProvider>> providerTypes = providers.stream()
                .map(ServiceLoader.Provider::type)
                .collect(Collectors.toList());

        assertThat(providerTypes).containsExactly(DiscoverableBrotliNativeProvider.class);
    }

    @Test
    void providerImplementationsCanBeInstantiatedFromServiceConfiguration() {
        ServiceConfigurationClassLoader classLoader = new ServiceConfigurationClassLoader(
                ServiceTest.class.getClassLoader(), LoadableBrotliNativeProvider.class.getName());
        ServiceLoader<BrotliNativeProvider> providers = ServiceLoader.load(BrotliNativeProvider.class, classLoader);

        List<String> platformNames = providers.stream()
                .map(ServiceLoader.Provider::get)
                .map(BrotliNativeProvider::platformName)
                .collect(Collectors.toList());

        assertThat(platformNames).containsExactly("loaded-test-platform");
    }

    private static String readPlatformName(BrotliNativeProvider provider) {
        return provider.platformName();
    }

    private static final class FixedBrotliNativeProvider implements BrotliNativeProvider {
        private final String platformName;

        private FixedBrotliNativeProvider(String platformName) {
            this.platformName = platformName;
        }

        @Override
        public String platformName() {
            return platformName;
        }
    }

    public static final class DiscoverableBrotliNativeProvider implements BrotliNativeProvider {
        @Override
        public String platformName() {
            return "test-platform";
        }
    }

    public static final class LoadableBrotliNativeProvider implements BrotliNativeProvider {
        @Override
        public String platformName() {
            return "loaded-test-platform";
        }
    }

    private static final class ServiceConfigurationClassLoader extends ClassLoader {
        private static final String SERVICE_CONFIGURATION_PATH = "META-INF/services/"
                + BrotliNativeProvider.class.getName();

        private final byte[] serviceConfiguration;

        private ServiceConfigurationClassLoader(ClassLoader parent, String providerClassName) {
            super(parent);
            this.serviceConfiguration = (providerClassName + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!SERVICE_CONFIGURATION_PATH.equals(name)) {
                return super.getResources(name);
            }

            List<URL> resources = new ArrayList<>(Collections.list(super.getResources(name)));
            resources.add(serviceConfigurationUrl());
            return Collections.enumeration(resources);
        }

        private URL serviceConfigurationUrl() throws IOException {
            return new URL(null, "memory:brotli4j-service-provider", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                            connected = true;
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(serviceConfiguration);
                        }
                    };
                }
            });
        }
    }
}
