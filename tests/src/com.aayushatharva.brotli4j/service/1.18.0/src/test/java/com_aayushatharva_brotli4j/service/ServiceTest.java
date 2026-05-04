/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_aayushatharva_brotli4j.service;

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
}
